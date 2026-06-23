import { fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { get, post, postAllowFailure } from '../lib/api.js';
import { prepareAccessTokens } from '../lib/accounts.js';

const targetUsers = Number(__ENV.USERS || 100);
const scheduleId = Number(__ENV.SCHEDULE_ID);
const countdownSeconds = Number(__ENV.COUNTDOWN_SECONDS || 5);
const reservationStartDelaySeconds = Number(__ENV.RESERVATION_START_DELAY_SECONDS || 30);
const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || 2);
const heartbeatIntervalSeconds = Number(__ENV.QUEUE_HEARTBEAT_INTERVAL_SECONDS || 10);
const maxPollCount = Number(__ENV.MAX_POLL_COUNT || 120);
const joinAfterOpenMillis = Number(__ENV.JOIN_AFTER_OPEN_MILLIS || 300);
const accountPreparationConcurrency = Number(__ENV.ACCOUNT_PREPARATION_CONCURRENCY || 20);
const seatStartId = Number(__ENV.SEAT_START_ID);
const seatsPerRequest = Number(__ENV.SEATS_PER_REQUEST || 1);
const reservationScenario = (__ENV.RESERVATION_SCENARIO || 'CREATE').toUpperCase();

export const reservationCreateSuccess = new Counter('reservation_create_success');
export const reservationCreateDuplicateRejected = new Counter('reservation_create_duplicate_rejected');
export const reservationCreateUnexpectedFailure = new Counter('reservation_create_unexpected_failure');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    reservation_create: {
      executor: 'per-vu-iterations',
      vus: targetUsers,
      iterations: 1,
      maxDuration: `${countdownSeconds + reservationStartDelaySeconds + maxPollCount * pollIntervalSeconds + 60}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:reservation.create}': ['p(95)<1500', 'p(99)<3000'],
  },
};

export function setup() {
  if (!Number.isInteger(scheduleId) || scheduleId <= 0) {
    fail('SCHEDULE_ID must be a positive integer');
  }
  if (!Number.isInteger(seatStartId) || seatStartId <= 0) {
    fail('SEAT_START_ID must be a positive integer');
  }
  if (seatsPerRequest < 1 || seatsPerRequest > 4) {
    fail('SEATS_PER_REQUEST must be 1..4');
  }
  if (!['CREATE', 'DUPLICATE'].includes(reservationScenario)) {
    fail(`unsupported RESERVATION_SCENARIO: ${reservationScenario}`);
  }

  const accessTokens = prepareAccessTokens(targetUsers, accountPreparationConcurrency);
  const openAtMillis = Date.now() + countdownSeconds * 1000;
  const reservationStartAtMillis = openAtMillis + reservationStartDelaySeconds * 1000;

  return {
    scheduleId,
    openAtMillis,
    reservationStartAtMillis,
    accessTokens,
  };
}

export default function (data) {
  const accessToken = data.accessTokens[__VU - 1];
  if (!accessToken) {
    fail(`access token is not prepared for VU ${__VU}`);
  }

  const waitMillis = data.openAtMillis + joinAfterOpenMillis - Date.now();
  if (waitMillis > 0) {
    sleep(waitMillis / 1000);
  }

  post(
    `/api/schedules/${data.scheduleId}/queues/join`,
    null,
    accessToken,
    null,
    'reservation.prepare.queue.join',
  );

  const entryToken = waitAndEnter(data, accessToken);
  const hold = post(
    `/api/schedules/${data.scheduleId}/seats/hold`,
    { seatIds: selectSeatIds() },
    accessToken,
    entryToken,
    'reservation.prepare.seat.hold',
  );

  const reservationWaitMillis = data.reservationStartAtMillis - Date.now();
  if (reservationWaitMillis > 0) {
    sleep(reservationWaitMillis / 1000);
  }

  const reservation = post(
    '/api/reservations',
    { holdId: hold.holdId },
    accessToken,
    entryToken,
    'reservation.create',
  );
  reservationCreateSuccess.add(1);

  if (reservationScenario === 'DUPLICATE') {
    verifyDuplicateRequest(hold.holdId, accessToken, entryToken);
  }

  if (!reservation.reservationId) {
    reservationCreateUnexpectedFailure.add(1);
  }
}

function selectSeatIds() {
  const offset = (__VU - 1) * seatsPerRequest;
  return Array.from(
    { length: seatsPerRequest },
    (_, index) => seatStartId + offset + index,
  );
}

function waitAndEnter(data, accessToken) {
  let lastHeartbeatAt = Date.now();
  for (let index = 0; index < maxPollCount; index += 1) {
    if (Date.now() - lastHeartbeatAt >= heartbeatIntervalSeconds * 1000) {
      post(
        `/api/schedules/${data.scheduleId}/queues/heartbeat`,
        null,
        accessToken,
        null,
        'reservation.prepare.queue.heartbeat',
      );
      lastHeartbeatAt = Date.now();
    }

    const position = get(
      `/api/schedules/${data.scheduleId}/queues/me`,
      accessToken,
      null,
      'reservation.prepare.queue.me',
    );

    if (position.status === 'ENTERABLE') {
      const entry = post(
        `/api/schedules/${data.scheduleId}/queues/enter`,
        null,
        accessToken,
        null,
        'reservation.prepare.queue.enter',
      );
      return entry.entryToken;
    }

    sleep(pollIntervalSeconds);
  }

  fail('queue enter timeout');
}

function verifyDuplicateRequest(holdId, accessToken, entryToken) {
  const response = postAllowFailure(
    '/api/reservations',
    { holdId },
    accessToken,
    entryToken,
    'reservation.create.duplicate',
  );

  if (response.status === 409) {
    reservationCreateDuplicateRejected.add(1);
    return;
  }
  reservationCreateUnexpectedFailure.add(1);
}
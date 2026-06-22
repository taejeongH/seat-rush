import { fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import {
  createPracticeQueueSession,
  get,
  post,
  postAllowFailure,
} from '../lib/api.js';
import { prepareAccessTokens } from '../lib/accounts.js';
import { uuid } from '../lib/random.js';

const targetUsers = Number(__ENV.USERS || 100);
const countdownSeconds = Number(__ENV.COUNTDOWN_SECONDS || 5);
const seatLayoutId = Number(__ENV.SEAT_LAYOUT_ID || 1);
const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || 2);
const heartbeatIntervalSeconds = Number(__ENV.QUEUE_HEARTBEAT_INTERVAL_SECONDS || 10);
const maxPollCount = Number(__ENV.MAX_POLL_COUNT || 120);
const joinAfterOpenMillis = Number(__ENV.JOIN_AFTER_OPEN_MILLIS || 300);
const accountPreparationConcurrency = Number(__ENV.ACCOUNT_PREPARATION_CONCURRENCY || 20);
const holdScenario = (__ENV.SEAT_HOLD_SCENARIO || 'DISTRIBUTED').toUpperCase();
const seatStartId = Number(__ENV.SEAT_START_ID || 1);
const seatPoolSize = Number(__ENV.SEAT_POOL_SIZE || 2500);
const seatsPerRequest = Number(__ENV.SEATS_PER_REQUEST || 1);

export const seatHoldSuccess = new Counter('seat_hold_success');
export const seatHoldConflict = new Counter('seat_hold_conflict');
export const seatHoldUnexpectedFailure = new Counter('seat_hold_unexpected_failure');

export const options = {
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    seat_hold: {
      executor: 'per-vu-iterations',
      vus: targetUsers,
      iterations: 1,
      maxDuration: `${countdownSeconds + maxPollCount * pollIntervalSeconds + 60}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:practice.seats.hold}': ['p(95)<1500', 'p(99)<3000'],
  },
};

export function setup() {
  if (!['DISTRIBUTED', 'CONTENTION'].includes(holdScenario)) {
    fail(`unsupported SEAT_HOLD_SCENARIO: ${holdScenario}`);
  }
  if (seatsPerRequest < 1 || seatsPerRequest > 4 || seatPoolSize < seatsPerRequest) {
    fail('SEATS_PER_REQUEST must be 1..4 and no larger than SEAT_POOL_SIZE');
  }

  const accessTokens = prepareAccessTokens(targetUsers, accountPreparationConcurrency);
  const practiceSessionId = uuid();
  const openAt = new Date(Date.now() + countdownSeconds * 1000);
  const closeAt = new Date(openAt.getTime() + 30 * 60 * 1000);

  createPracticeQueueSession(
    seatLayoutId,
    practiceSessionId,
    openAt.toISOString(),
    closeAt.toISOString(),
  );

  return {
    practiceSessionId,
    seatLayoutId,
    openAtMillis: openAt.getTime(),
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
    `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/join`,
    null,
    accessToken,
    null,
    'practice.queue.join',
  );

  const entryToken = waitAndEnter(data, accessToken);
  const response = postAllowFailure(
    `/api/schedules/${data.seatLayoutId}/seats/hold`,
    { seatIds: selectSeatIds() },
    accessToken,
    entryToken,
    'practice.seats.hold',
  );

  if (response.status >= 200 && response.status < 300 && response.body?.holdId) {
    seatHoldSuccess.add(1);
    return;
  }
  if (response.status === 409) {
    seatHoldConflict.add(1);
    return;
  }
  seatHoldUnexpectedFailure.add(1);
}

function selectSeatIds() {
  if (holdScenario === 'CONTENTION') {
    return Array.from(
      { length: seatsPerRequest },
      (_, index) => seatStartId + index,
    );
  }

  const offset = ((__VU - 1) * seatsPerRequest) % seatPoolSize;
  return Array.from(
    { length: seatsPerRequest },
    (_, index) => seatStartId + ((offset + index) % seatPoolSize),
  );
}

function waitAndEnter(data, accessToken) {
  let lastHeartbeatAt = Date.now();
  for (let index = 0; index < maxPollCount; index += 1) {
    if (Date.now() - lastHeartbeatAt >= heartbeatIntervalSeconds * 1000) {
      post(
        `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/heartbeat`,
        null,
        accessToken,
        null,
        'practice.queue.heartbeat',
      );
      lastHeartbeatAt = Date.now();
    }

    const position = get(
      `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/me`,
      accessToken,
      null,
      'practice.queue.me',
    );

    if (position.status === 'ENTERABLE') {
      const entry = post(
        `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/enter`,
        null,
        accessToken,
        null,
        'practice.queue.enter',
      );
      return entry.entryToken;
    }

    sleep(pollIntervalSeconds);
  }

  fail('queue enter timeout');
}

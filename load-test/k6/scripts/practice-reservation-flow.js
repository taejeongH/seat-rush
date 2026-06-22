import { fail, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  createPracticeQueueSession,
  get,
  post,
  postAllowFailure,
} from '../lib/api.js';
import { prepareAccessTokens } from '../lib/accounts.js';
import { pickOne, randomInt, shuffle, uuid, weightedResult } from '../lib/random.js';

const targetUsers = Number(__ENV.USERS || 100);
const countdownSeconds = Number(__ENV.COUNTDOWN_SECONDS || 60);
const seatLayoutId = Number(__ENV.SEAT_LAYOUT_ID || 1);
const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || 2);
const heartbeatIntervalSeconds = Number(__ENV.QUEUE_HEARTBEAT_INTERVAL_SECONDS || 10);
const maxPollCount = Number(__ENV.MAX_POLL_COUNT || 120);
const joinAfterOpenMillis = Number(__ENV.JOIN_AFTER_OPEN_MILLIS || 300);
const maxSeatsPerUser = Number(__ENV.MAX_SEATS_PER_USER || 4);
const seatHoldRetryCount = Number(__ENV.SEAT_HOLD_RETRY_COUNT || 5);
const paymentSuccessPercent = Number(__ENV.PAYMENT_SUCCESS_PERCENT || 100);
const paymentFailurePercent = Number(__ENV.PAYMENT_FAILURE_PERCENT || 0);
const accountPreparationConcurrency = Number(__ENV.ACCOUNT_PREPARATION_CONCURRENCY || 20);

export const reservationConfirmed = new Counter('reservation_confirmed');
export const paymentFailed = new Counter('payment_failed');
export const userAbandoned = new Counter('user_abandoned');
export const seatHoldRetryConflict = new Counter('seat_hold_retry_conflict');
export const seatHoldFinalFailure = new Counter('seat_hold_final_failure');
export const flowDuration = new Trend('practice_reservation_flow_duration_ms');

export const options = {
  // 계정 토큰 준비는 측정 시작 전 단계이므로 사용자 수에 따라 별도 시간을 허용합니다.
  setupTimeout: __ENV.SETUP_TIMEOUT || '10m',
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    reservation_flow: {
      executor: 'per-vu-iterations',
      vus: targetUsers,
      iterations: 1,
      maxDuration: `${countdownSeconds + maxPollCount * pollIntervalSeconds + 120}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:practice.queue.join}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{name:practice.queue.enter}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{name:practice.seats}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{name:practice.seats.hold}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{name:practice.reservation.create}': ['p(95)<1500', 'p(99)<3000'],
    'http_req_duration{name:practice.payment.complete}': ['p(95)<1500', 'p(99)<3000'],
  },
};

export function setup() {
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
    runId: uuid().slice(0, 8),
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

  const flowStartedAt = Date.now();

  post(
    `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/join`,
    null,
    accessToken,
    null,
    'practice.queue.join',
  );

  const entryToken = waitAndEnter(data, accessToken);
  const hold = holdSeatsWithRetry(data, accessToken, entryToken);
  if (!hold) {
    userAbandoned.add(1);
    return;
  }

  const paymentDecision = weightedResult(paymentSuccessPercent, paymentFailurePercent);
  if (paymentDecision === 'ABANDON') {
    userAbandoned.add(1);
    return;
  }

  const reservation = post(
    '/api/practice-reservations/reservations',
    { holdId: hold.holdId },
    accessToken,
    entryToken,
    'practice.reservation.create',
  );
  const payment = post(
    `/api/practice-reservations/sessions/${data.practiceSessionId}/reservations/${reservation.reservationId}/payments`,
    null,
    accessToken,
    null,
    'practice.payment.request',
  );

  post(
    `/api/practice-reservations/sessions/${data.practiceSessionId}/payments/${payment.paymentId}/complete`,
    { result: paymentDecision },
    accessToken,
    null,
    'practice.payment.complete',
  );

  const finalReservation = pollReservation(data, reservation.reservationId, accessToken);
  if (finalReservation.status === 'CONFIRMED') {
    reservationConfirmed.add(1);
  } else {
    paymentFailed.add(1);
  }
  flowDuration.add(Date.now() - flowStartedAt);
}

/**
 * 측정 시작 전에 테스트 계정의 토큰을 제한된 동시성으로 갱신합니다.
 * 실제 성능 측정 구간에는 로그인 요청을 포함하지 않습니다.
 */
function holdSeatsWithRetry(data, accessToken, entryToken) {
  const sections = get(
    `/api/practice-reservations/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/sections`,
    accessToken,
    entryToken,
    'practice.sections',
  );

  for (let retry = 0; retry < seatHoldRetryCount; retry += 1) {
    const section = pickOne(sections);
    const seats = get(
      `/api/practice-reservations/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/seats?sectionId=${section.sectionId}`,
      accessToken,
      entryToken,
      'practice.seats',
    );
    const availableSeatIds = shuffle(
      seats
        .filter((seat) => seat.status === 'AVAILABLE')
        .map((seat) => seat.seatId),
    );

    if (availableSeatIds.length === 0) {
      continue;
    }

    const seatCount = Math.min(availableSeatIds.length, randomInt(1, maxSeatsPerUser));
    const seatIds = availableSeatIds.slice(0, seatCount);
    const response = postAllowFailure(
      `/api/schedules/${data.seatLayoutId}/seats/hold`,
      { seatIds },
      accessToken,
      entryToken,
      'practice.seats.hold',
    );

    if (response.status >= 200 && response.status < 300 && response.body?.holdId) {
      return response.body;
    }

    seatHoldRetryConflict.add(1);
  }

  seatHoldFinalFailure.add(1);
  return null;
}

function waitAndEnter(data, accessToken) {
  let lastHeartbeatAt = Date.now();
  for (let i = 0; i < maxPollCount; i += 1) {
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

  throw new Error('queue enter timeout');
}

function pollReservation(data, reservationId, accessToken) {
  for (let i = 0; i < maxPollCount; i += 1) {
    const reservation = get(
      `/api/practice-reservations/sessions/${data.practiceSessionId}/reservations/${reservationId}`,
      accessToken,
      null,
      'practice.reservation.get',
    );
    if (!['PENDING_PAYMENT', 'PAYMENT_PROCESSING'].includes(reservation.status)) {
      return reservation;
    }
    sleep(pollIntervalSeconds);
  }

  throw new Error('reservation completion timeout');
}

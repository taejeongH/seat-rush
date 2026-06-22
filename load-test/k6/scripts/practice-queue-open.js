import { sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  createPracticeQueueSession,
  get,
  post,
} from '../lib/api.js';
import { prepareAccessTokens } from '../lib/accounts.js';
import { uuid } from '../lib/random.js';

const targetUsers = Number(__ENV.USERS || 100);
const countdownSeconds = Number(__ENV.COUNTDOWN_SECONDS || 60);
const seatLayoutId = Number(__ENV.SEAT_LAYOUT_ID || 1);
const pollIntervalSeconds = Number(__ENV.POLL_INTERVAL_SECONDS || 1);
const maxPollCount = Number(__ENV.MAX_POLL_COUNT || 120);
const joinAfterOpenMillis = Number(__ENV.JOIN_AFTER_OPEN_MILLIS || 300);
const accountPreparationConcurrency = Number(__ENV.ACCOUNT_PREPARATION_CONCURRENCY || 20);

export const queueJoinSuccess = new Counter('queue_join_success');
export const queueEnterSuccess = new Counter('queue_enter_success');
export const queueEnterableWait = new Trend('queue_enterable_wait_ms');

export const options = {
  scenarios: {
    queue_open: {
      executor: 'per-vu-iterations',
      vus: targetUsers,
      iterations: 1,
      maxDuration: `${countdownSeconds + maxPollCount * pollIntervalSeconds + 60}s`,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{name:practice.queue.join}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{name:practice.queue.me}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_duration{name:practice.queue.enter}': ['p(95)<1000', 'p(99)<2000'],
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
    practiceSessionId,
    seatLayoutId,
    openAtMillis: openAt.getTime(),
    accessTokens,
  };
}

export default function (data) {
  const accessToken = data.accessTokens[__VU - 1];
  if (!accessToken) {
    throw new Error(`access token is not prepared for VU ${__VU}`);
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
  queueJoinSuccess.add(1);

  const startedAt = Date.now();
  for (let i = 0; i < maxPollCount; i += 1) {
    const position = get(
      `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/me`,
      accessToken,
      null,
      'practice.queue.me',
    );

    if (position.status === 'ENTERABLE') {
      queueEnterableWait.add(Date.now() - startedAt);
      const entry = post(
        `/api/practice/sessions/${data.practiceSessionId}/seat-layouts/${data.seatLayoutId}/queues/enter`,
        null,
        accessToken,
        null,
        'practice.queue.enter',
      );
      if (entry.entryToken) {
        queueEnterSuccess.add(1);
      }
      return;
    }

    sleep(pollIntervalSeconds);
  }
}

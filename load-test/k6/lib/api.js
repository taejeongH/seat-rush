import http from 'k6/http';
import { check, fail } from 'k6';

export function baseUrl() {
  return (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
}

export function jsonHeaders(accessToken, entryToken) {
  const headers = {
    'Content-Type': 'application/json',
  };

  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  if (entryToken) {
    headers['X-Entry-Token'] = entryToken;
  }

  return headers;
}

export function unwrap(response, name, allowFailure = false) {
  const ok = check(response, {
    [`${name} status is 2xx`]: (res) => res.status >= 200 && res.status < 300,
  });

  let body = null;
  try {
    body = response.json();
  } catch (error) {
    if (allowFailure) {
      return null;
    }
    fail(`${name} returned non-json response: status=${response.status}`);
  }

  if (!ok || !body?.isSuccess) {
    if (allowFailure) {
      return body;
    }
    fail(`${name} failed: status=${response.status}, code=${body?.code}, message=${body?.message}`);
  }

  return body.result;
}

export function post(path, body, accessToken, entryToken, name) {
  const response = http.post(
    `${baseUrl()}${path}`,
    body === null || body === undefined ? null : JSON.stringify(body),
    {
      headers: jsonHeaders(accessToken, entryToken),
      tags: { name },
    },
  );

  return unwrap(response, name);
}

export function postAllowFailure(path, body, accessToken, entryToken, name) {
  const response = http.post(
    `${baseUrl()}${path}`,
    body === null || body === undefined ? null : JSON.stringify(body),
    {
      headers: jsonHeaders(accessToken, entryToken),
      tags: { name },
      responseCallback: http.expectedStatuses({ min: 200, max: 399 }, 400, 409),
    },
  );

  let parsedBody = null;
  try {
    parsedBody = response.json();
  } catch (error) {
    return {
      status: response.status,
      body: null,
    };
  }

  return {
    status: response.status,
    body: parsedBody?.isSuccess ? parsedBody.result : parsedBody,
  };
}

export function get(path, accessToken, entryToken, name) {
  const response = http.get(`${baseUrl()}${path}`, {
    headers: jsonHeaders(accessToken, entryToken),
    tags: { name },
  });

  return unwrap(response, name);
}

export function signupOrIgnore(email, password, name) {
  const response = http.post(
    `${baseUrl()}/api/auth/signup`,
    JSON.stringify({ email, password, name }),
    {
      headers: jsonHeaders(),
      tags: { name: 'auth.signup' },
    },
  );

  const body = unwrap(response, 'auth.signup', true);
  if (body?.isSuccess === false && body?.code !== 'AUTH001' && body?.code !== 'EMAIL_ALREADY_EXISTS') {
    fail(`auth.signup failed: status=${response.status}, code=${body?.code}, message=${body?.message}`);
  }
}

export function login(email, password) {
  const result = post(
    '/api/auth/login',
    { email, password },
    null,
    null,
    'auth.login',
  );

  return result.accessToken;
}

export function createPracticeQueueSession(seatLayoutId, practiceSessionId, bookingOpenAt, bookingCloseAt) {
  return post(
    '/api/practice/queues/sessions',
    {
      seatLayoutId,
      practiceSessionId,
      bookingOpenAt,
      bookingCloseAt,
    },
    null,
    null,
    'practice.queue.session.create',
  );
}

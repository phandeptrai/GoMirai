import http from 'k6/http';
import { check, sleep } from 'k6';
import {
  BASE_URL,
  jsonHeaders,
  parseBody,
  randomPhone,
  extractBookingId,
  unwrapData,
  CUSTOMER_PHONE,
  CUSTOMER_PASSWORD,
} from '../SmokeTest/config.js';

function envNum(name, fallback) {
  const v = __ENV[name];
  if (v == null || v === '') return fallback;
  const n = Number(v);
  return Number.isFinite(n) ? n : fallback;
}

function pickBaseUrl() {
  return __ENV.BASE_URL || BASE_URL;
}

function registerCustomer(baseUrl, phone, password) {
  const res = http.post(
    `${baseUrl}/api/auth/register`,
    JSON.stringify({ phoneNumber: phone, password }),
    { headers: jsonHeaders(), tags: { endpoint: 'auth.register', name: 'auth.register' } }
  );
  const body = parseBody(res);
  const token = body?.accessToken || null;
  return { res, body, token };
}

function loginCustomer(baseUrl, phone, password) {
  const res = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ phoneNumber: phone, password }),
    { headers: jsonHeaders(), tags: { endpoint: 'auth.login', name: 'auth.login' } }
  );
  const body = parseBody(res);
  const token = body?.accessToken || null;
  const userId = body?.userId || null;
  return { res, body, token, userId };
}

function createBooking(baseUrl, token) {
  const res = http.post(
    `${baseUrl}/api/booking`,
    JSON.stringify({
      pickupLocation: { fullAddress: 'k6 pickup', latitude: 10.7769, longitude: 106.7009 },
      dropoffLocation: { fullAddress: 'k6 dropoff', latitude: 10.7756, longitude: 106.7002 },
      vehicleType: __ENV.VEHICLE_TYPE || 'CAR_4',
      paymentMethod: __ENV.PAYMENT_METHOD || 'CASH',
      notes: 'k6 load flow',
      // Simulate client-side calculation (optimization)
      estimatedDistanceKm: 5.2,
      estimatedDurationMinutes: 15,
    }),
    { headers: jsonHeaders(token), tags: { endpoint: 'booking.create', name: 'booking.create' } }
  );
  const body = parseBody(res);
  const bookingId = extractBookingId(body);
  return { res, body, bookingId };
}

function getBooking(baseUrl, token, bookingId) {
  const res = http.get(`${baseUrl}/api/booking/${bookingId}`, {
    headers: jsonHeaders(token),
    tags: { endpoint: 'booking.get', name: 'booking.get' },
  });
  const body = parseBody(res);
  const dto = unwrapData(body);
  return { res, body, dto };
}

function debugEnabled(name) {
  const v = __ENV[name];
  return v === '1' || v === 'true' || v === 'TRUE' || v === 'yes' || v === 'YES';
}

function maybeLogGetBookingFailure(baseUrl, bookingId, token, res, body) {
  if (!debugEnabled('DEBUG_GET_BOOKING')) return;

  const sampleRateRaw = __ENV.DEBUG_SAMPLE_RATE;
  const sampleRate = sampleRateRaw == null || sampleRateRaw === '' ? 0.02 : Number(sampleRateRaw);
  const rate = Number.isFinite(sampleRate) ? Math.max(0, Math.min(1, sampleRate)) : 0.02;
  if (Math.random() > rate) return;

  const url = `${baseUrl}/api/booking/${bookingId}`;
  const tokenTail = token ? String(token).slice(-12) : 'null';
  console.error(
    `[k6][getBooking failed] url=${url} status=${res?.status} token..=${tokenTail} body=${JSON.stringify(body)}`
  );
}

function maybeLogCreateBookingFailure(baseUrl, token, res, body) {
  if (!debugEnabled('DEBUG_CREATE_BOOKING')) return;

  const sampleRateRaw = __ENV.DEBUG_SAMPLE_RATE;
  const sampleRate = sampleRateRaw == null || sampleRateRaw === '' ? 0.02 : Number(sampleRateRaw);
  const rate = Number.isFinite(sampleRate) ? Math.max(0, Math.min(1, sampleRate)) : 0.02;
  if (Math.random() > rate) return;

  const url = `${baseUrl}/api/booking`;
  const tokenTail = token ? String(token).slice(-12) : 'null';
  console.error(
    `[k6][createBooking failed] url=${url} status=${res?.status} token..=${tokenTail} body=${JSON.stringify(body)}`
  );
}

function cancelBooking(baseUrl, token, bookingId) {
  const res = http.post(
    `${baseUrl}/api/booking/${bookingId}/cancel`,
    JSON.stringify({ reason: 'k6 cancel' }),
    { headers: jsonHeaders(token), tags: { endpoint: 'booking.cancel', name: 'booking.cancel' } }
  );
  const body = parseBody(res);
  return { res, body };
}

function resolveTokenForUser(baseUrl, phone, password) {
  const reg = registerCustomer(baseUrl, phone, password);
  if (reg.token) return reg.token;
  const l = loginCustomer(baseUrl, phone, password);
  return l.token;
}

/**
 * SmokeTest-style setup:
 * create a pool of users once, reuse tokens during the run.
 *
 * Env:
 * - USER_POOL (default 20)
 * - REG_PASSWORD (default TestPass123)
 * - SETUP_SLEEP_MS (default 0)
 */
export function setupLoginBookingUserPool() {
  const baseUrl = pickBaseUrl();
  const userPool = Math.max(1, envNum('USER_POOL', 20));
  const password = __ENV.REG_PASSWORD || 'TestPass123';
  const setupSleepMs = envNum('SETUP_SLEEP_MS', 0);

  const users = [];
  for (let i = 0; i < userPool; i++) {
    const phone = randomPhone();
    const token = resolveTokenForUser(baseUrl, phone, password);
    if (token) users.push({ phone, password, token });
    if (setupSleepMs > 0) sleep(setupSleepMs / 1000);
  }

  // fallback to pre-existing account if pool creation fails
  if (users.length === 0) {
    const phone = __ENV.CUSTOMER_PHONE || CUSTOMER_PHONE;
    const pw = __ENV.CUSTOMER_PASSWORD || CUSTOMER_PASSWORD;
    const l = loginCustomer(baseUrl, phone, pw);
    if (l.token) users.push({ phone, password: pw, token: l.token });
  }

  return { baseUrl, users };
}

/**
 * One full iteration:
 * - create booking
 * - get booking
 * - optionally cancel booking
 *
 * Env knobs:
 * - BASE_URL
 * - SLEEP_MS (default 100)
 * - CANCEL_RATE (0..1, default 0.2)
 */
export function runLoginToBookingFlow(data) {
  const baseUrl = data?.baseUrl || pickBaseUrl();
  const sleepMs = envNum('SLEEP_MS', 100);
  const cancelRate = Math.max(0, Math.min(1, Number(__ENV.CANCEL_RATE ?? 0.2)));

  const users = Array.isArray(data?.users) ? data.users : [];
  if (users.length === 0) return;

  const u = users[(__VU - 1) % users.length];
  const token = u?.token || null;
  if (!token) return;

  const created = createBooking(baseUrl, token);
  if (created?.res?.status !== 201) {
    maybeLogCreateBookingFailure(baseUrl, token, created?.res, created?.body);
  }
  check(created.res, {
    'create booking: status 201': (x) => x.status === 201,
  });
  if (created.res.status !== 201) return;

  // GET may be eventually consistent right after create → small retry.
  let got = null;
  const getRetries = Math.max(1, envNum('GET_BOOKING_RETRIES', 5));
  const retrySleepMs = Math.max(0, envNum('GET_BOOKING_RETRY_SLEEP_MS', 250));
  for (let i = 0; i < getRetries; i++) {
    got = getBooking(baseUrl, token, created.bookingId);
    if (got.res.status === 200) break;
    if (retrySleepMs > 0) sleep(retrySleepMs / 1000);
  }
  if (got?.res?.status !== 200) {
    maybeLogGetBookingFailure(baseUrl, created.bookingId, token, got?.res, got?.body);
  }
  check(got.res, {
    'get booking: status 200': (x) => x.status === 200,
    'get booking: bookingId matches': () => got.res.status !== 200 || (got.dto && got.dto.bookingId === created.bookingId),
  });

  if (Math.random() < cancelRate) {
    const c = cancelBooking(baseUrl, token, created.bookingId);
    check(c.res, {
      'cancel booking: 200 or 4xx': (x) => x.status === 200 || x.status >= 400,
    });
  }

  sleep(sleepMs / 1000);
}


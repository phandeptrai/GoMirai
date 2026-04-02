/**
 * Auth & User Service tests.
 *
 * Standalone : k6 run auth.test.js
 * Via main.js: import runAuthTests from './auth.test.js'
 *
 * Response structures:
 *   AuthResponse : { userId, role, accessToken, tokenType }
 *   UserProfileResponse: { userId, fullName, phone, email, address, dateOfBirth, status }
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody, randomPhone, unwrapData, sameUuid,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './config.js';

// ── Standalone config (ignored when imported from main.js) ──────────────────
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  const customerPhone = randomPhone();
  const res = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: customerPhone, password: 'TestPass123' }),
    { headers: jsonHeaders() }
  );
  const body = parseBody(res);
  return {
    customerPhone,
    customerToken: body.accessToken || null,
    customerId:    body.userId    || null,
  };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runAuthTests(data = {}) {
  const { customerToken, customerId, customerPhone } = data;

  // ── 1. Registration ───────────────────────────────────────────────────────
  group('Auth - Registration', () => {
    const freshPhone = randomPhone();

    // TC-01: Đăng ký thành công → response có accessToken, userId, role=CUSTOMER
    let res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: freshPhone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    let body = parseBody(res);
    
    if (res.status !== 201 && res.status !== 200) {
      console.warn(`[TC-01] Register failed: status=${res.status} phone=${freshPhone} body=${res.body}`);
    }

    check(res, {
      '[TC-01] Register - success status':        () => res.status === 201 || res.status === 200,
      '[TC-01] Register - accessToken exists':    () => body.accessToken != null,
      '[TC-01] Register - role is CUSTOMER':      () => 
        body.role === 'CUSTOMER' || body.role === 'ROLE_CUSTOMER',
    });

    // TC-02: Trùng SĐT → response KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: freshPhone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-02] Duplicate phone - no accessToken': () => !body.accessToken,
    });

    // TC-03: SĐT sai định dạng → response KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: 'abc', password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-03] Invalid phone - no accessToken': () => !body.accessToken,
    });

    // TC-04: Mật khẩu yếu → response KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: randomPhone(), password: '123' }),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-04] Weak password - no accessToken': () => !body.accessToken,
    });
  });

  // ── 2. Login ──────────────────────────────────────────────────────────────
  group('Auth - Login', () => {
    const phone = customerPhone || randomPhone();

    // TC-05: Đăng nhập thành công → có accessToken và userId
    let res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ phoneNumber: phone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    let body = parseBody(res);
    check(res, {
      '[TC-05] Login - accessToken is string': () => typeof body.accessToken === 'string' && body.accessToken.length > 10,
      '[TC-05] Login - userId exists':         () => body.userId != null,
    });

    // TC-06: Sai mật khẩu → KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ phoneNumber: phone, password: 'WrongPass999' }),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-06] Wrong password - no accessToken': () => !body.accessToken,
    });

    // TC-07: SĐT không tồn tại → KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ phoneNumber: '0900000001', password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-07] User not found - no accessToken': () => !body.accessToken,
    });

    // TC-08: Body rỗng → KHÔNG có accessToken
    res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({}),
      { headers: jsonHeaders() });
    body = parseBody(res);
    check(res, {
      '[TC-08] Empty body - no accessToken': () => !body.accessToken,
    });
  });

  // ── 3. User Profile ───────────────────────────────────────────────────────
  group('User - Profile', () => {
    // Use pre-existing CUSTOMER account which is guaranteed to have a COMPLETE profile
    // (freshly registered user profile may still be in PENDING state due to Kafka async sync)
    let loginRes = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ phoneNumber: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD }),
      { headers: jsonHeaders() });
    let loginBody = parseBody(loginRes);
    const existingToken = loginBody.accessToken || null;
    const existingUserId = loginBody.userId || null;

    if (!existingToken || !existingUserId) {
      console.warn('Auth: skipping profile tests (pre-existing CUSTOMER login failed)');
      return;
    }

    // TC-09: Lấy profile → có userId khớp + phone là string (profile đã COMPLETE)
    let res = http.get(`${BASE_URL}/api/users/${existingUserId}`,
      { headers: jsonHeaders(existingToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-09] Get profile - userId matches': () => sameUuid(body.userId, existingUserId),
      '[TC-09] Get profile - phone is string': () => typeof body.phone === 'string',
    });

    // TC-10: Cập nhật profile → fullName đã được cập nhật đúng trong response
    res = http.put(`${BASE_URL}/api/users/${existingUserId}`,
      JSON.stringify({ fullName: 'User Test Updated', email: 'k6test@gomirai.vn' }),
      { headers: jsonHeaders(existingToken) });
    body = unwrapData(parseBody(res));
    check(res, {
      '[TC-10] Update profile - fullName updated': () =>
        res.status === 200 && String(body.fullName || '').trim() === 'User Test Updated',
      '[TC-10] Update profile - userId preserved': () =>
        res.status === 200 && sameUuid(body.userId, existingUserId),
    });

    // TC-11: Email không hợp lệ → response KHÔNG chứa userId (error response)
    res = http.put(`${BASE_URL}/api/users/${existingUserId}`,
      JSON.stringify({ email: 'not-an-email' }),
      { headers: jsonHeaders(existingToken) });
    body = parseBody(res);
    check(res, {
      '[TC-11] Invalid email - no userId in response': () => body.userId === undefined || body.userId === null,
    });

    // TC-12: Không có auth header → 401 (kiểm tra bảo mật)
    res = http.get(`${BASE_URL}/api/users/${existingUserId}`);
    check(res, {
      '[TC-12] No auth - 401': r => r.status === 401,
    });
  });
}

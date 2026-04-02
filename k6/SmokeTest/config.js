/**
 * GoMirai k6 Test Configuration
 * Shared helpers and configuration for all test scripts.
 */

import encoding from 'k6/encoding';

// Base URL — luôn set khi test GKE: k6 run -e BASE_URL=http://<EXTERNAL-IP> main.js
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Pre-existing test accounts (must exist in the database before running tests)
export const ADMIN_PHONE = __ENV.ADMIN_PHONE || '01111111111';
export const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || 'TestPass123';
export const DRIVER_PHONE = __ENV.DRIVER_PHONE || '0798052860';
export const DRIVER_PASSWORD = __ENV.DRIVER_PASSWORD || 'TestPass123';
export const CUSTOMER_PHONE = __ENV.CUSTOMER_PHONE || '0123456788';
export const CUSTOMER_PASSWORD = __ENV.CUSTOMER_PASSWORD || 'Phandeptrai123';

/**
 * Returns HTTP headers with optional Bearer token.
 */
export function jsonHeaders(token) {
  const h = { 'Content-Type': 'application/json' };
  if (token) h['Authorization'] = `Bearer ${token}`;
  return h;
}

// Internal API Key - phải khớp với giá trị INTERNAL_API_KEY trong .env / k8s secret
export const INTERNAL_API_KEY = __ENV.INTERNAL_API_KEY || 'gomirai-internal-s3cr3t-k3y-2026-ch4ng3-m3-1n-pr0ductI0n';

/**
 * Returns HTTP headers for internal service-to-service calls (no user JWT).
 */
export function internalHeaders() {
  return {
    'Content-Type': 'application/json',
    'X-Internal-Api-Key': INTERNAL_API_KEY,
  };
}

/**
 * Generates a random Vietnamese phone number.
 */
export function randomPhone() {
  const prefix = ['09', '07', '08', '03'][Math.floor(Math.random() * 4)];
  const n = Math.floor(Math.random() * 90000000) + 10000000;
  return `${prefix}${n}`;
}

/**
 * Safely parses a JSON response body. Returns {} on parse error.
 */
export function parseBody(res) {
  try {
    const parsed = JSON.parse(res.body);
    return parsed ?? {};
  } catch (_) {
    return {};
  }
}

/** So sánh UUID (string) từ JSON — tránh lệch kiểu khiến check fail trên GKE. */
export function sameUuid(a, b) {
  if (a == null || b == null) return false;
  return String(a).toLowerCase() === String(b).toLowerCase();
}

/**
 * Một số gateway/service bọc ApiResponse { success, data }; microservice có thể trả DTO thẳng.
 */
export function unwrapData(body) {
  if (!body || typeof body !== 'object') return body;
  if (body.data != null && typeof body.data === 'object' && body.success === true) {
    return body.data;
  }
  return body;
}

/** Booking POST trả ApiResponse<BookingResponse> — bookingId nằm trong data. */
export function extractBookingId(body) {
  const b = body || {};
  if (b.data && b.data.bookingId != null) return b.data.bookingId;
  if (b.bookingId != null) return b.bookingId;
  return null;
}

/** Spring Page (public GET) hoặc bọc trong data — lấy object có content/totalElements. */
export function unwrapPage(body) {
  const u = unwrapData(body);
  if (u && u.content !== undefined) return u;
  if (body && body.content !== undefined) return body;
  return u || body || {};
}

/** ValidationErrorResponse: errors[field] = message */
export function hasFieldError(body, field) {
  const e = body && body.errors;
  if (!e || typeof e !== 'object') return false;
  return e[field] != null && e[field] !== '';
}

/** Có bất kỳ lỗi field validation nào */
export function hasAnyFieldError(body) {
  const e = body && body.errors;
  return e && typeof e === 'object' && Object.keys(e).length > 0;
}

/**
 * Đọc claim `role` từ JWT (base64url) — dùng cho log debug; atob() không đúng với base64url.
 */
export function jwtPayloadRole(token) {
  if (!token || typeof token !== 'string') return 'unknown';
  const parts = token.split('.');
  if (parts.length !== 3) return 'unknown';
  try {
    const b64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = b64.length % 4 === 0 ? '' : '='.repeat(4 - (b64.length % 4));
    const json = encoding.b64decode(b64 + pad, 'std', 's');
    const p = JSON.parse(json);
    return p.role || 'no-role';
  } catch (_) {
    return 'unknown';
  }
}

/** Merge jsonHeaders + metric tag `endpoint` cho threshold theo từng API */
export function taggedOpts(token, endpoint, extra = {}) {
  return Object.assign({ headers: jsonHeaders(token), tags: { endpoint } }, extra);
}

/**
 * Logs in a user and returns { token, userId } or null on failure.
 */
export function loginUser(http, phone, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: phone, password }),
    { headers: jsonHeaders() }
  );
  if (res.status === 200) {
    const body = parseBody(res);
    return { token: body.accessToken, userId: body.userId };
  }
  console.error(`Login failed for ${phone}: ${res.status} - ${res.body}`);
  return null;
}

/**
 * Registers a new user and returns { token, userId } or null on failure.
 */
export function registerUser(http, phone, password) {
  const res = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: phone, password }),
    { headers: jsonHeaders() }
  );
  if (res.status === 200 || res.status === 201) {
    const body = parseBody(res);
    return { token: body.accessToken, userId: body.userId };
  }
  console.error(`Register failed for ${phone}: ${res.status} - ${res.body}`);
  return null;
}

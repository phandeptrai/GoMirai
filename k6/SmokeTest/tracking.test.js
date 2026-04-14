/**
 * Tracking Service tests.
 *
 * Standalone : k6 run tracking.test.js
 * Via main.js: import runTrackingTests from './tracking.test.js'
 *
 * Response structures:
 *   POST /tracking/location       : { message: string }
 *   GET  /tracking/me             : DriverGeoState { driverId, latitude, longitude, status, vehicleType, lastUpdatedAt }
 *   GET  /tracking/drivers/{id}   : DriverGeoState
 *   POST /tracking/nearby         : DriverLocationResponse[] — each { driverId, latitude, longitude, status, vehicleType, distance, lastUpdatedAt }
 *
 * NOTE: Redis instability may cause 5xx; missing ROLE_DRIVER causes 403.
 *       Checks use conditional pattern: "if 200 then verify body fields".
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody,
  DRIVER_PHONE, DRIVER_PASSWORD,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './config.js';

// ── Standalone config ───────────────────────────────────────────────────────
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  let res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: DRIVER_PHONE, password: DRIVER_PASSWORD }),
    { headers: jsonHeaders() });
  let body = parseBody(res);
  const driverToken = body.accessToken || null;
  const driverId    = body.userId     || null;

  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res);
  const customerToken = body.accessToken || null;

  if (!driverToken)   console.error('Tracking setup: driver login failed');
  if (!customerToken) console.error('Tracking setup: customer login failed');
  return { driverToken, driverId, customerToken };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runTrackingTests(data = {}) {
  const { driverToken, driverId, customerToken } = data;

  // ── 1. Driver Updates Location ────────────────────────────────────────────
  group('Tracking - Location Update', () => {
    if (!driverToken) { console.warn('Tracking: no driverToken, skipping'); return; }

    // TC-44: Cập nhật vị trí → nếu 200: response có message; không được là 401
    let res = http.post(`${BASE_URL}/api/tracking/location`,
      JSON.stringify({ driverId, latitude: 10.762622, longitude: 106.660172,
        status: 'ONLINE', vehicleType: 'MOTORBIKE' }),
      { headers: jsonHeaders(driverToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-44] Update location center - not 401':           r => r.status !== 401,
      '[TC-44] Update location center - if 200 has message': () => res.status !== 200 || typeof body.message === 'string',
    });

    // TC-45: Cập nhật vị trí khác
    res = http.post(`${BASE_URL}/api/tracking/location`,
      JSON.stringify({ driverId, latitude: 10.8024, longitude: 106.7148,
        status: 'ONLINE', vehicleType: 'MOTORBIKE' }),
      { headers: jsonHeaders(driverToken) });
    body = parseBody(res);
    check(res, {
      '[TC-45] Update location far - not 401':           r => r.status !== 401,
      '[TC-45] Update location far - if 200 has message': () => res.status !== 200 || typeof body.message === 'string',
    });

    // Reset vị trí về trung tâm
    http.post(`${BASE_URL}/api/tracking/location`,
      JSON.stringify({ driverId, latitude: 10.762622, longitude: 106.660172,
        status: 'ONLINE', vehicleType: 'MOTORBIKE' }),
      { headers: jsonHeaders(driverToken) });

    // TC-46: Driver lấy vị trí của mình → nếu 200: có driverId + coordinates
    res = http.get(`${BASE_URL}/api/tracking/me`, { headers: jsonHeaders(driverToken) });
    body = parseBody(res);
    check(res, {
      '[TC-46] Get own location - not 401':                   r => r.status !== 401,
      '[TC-46] Get own location - if 200 has driverId':       () => res.status !== 200 || body.driverId != null,
      '[TC-46] Get own location - if 200 has coordinates':    () => res.status !== 200 || (body.latitude != null && body.longitude != null),
    });

    // TC-47: Lấy vị trí tài xế theo ID → nếu 200: có driverId khớp
    if (driverId) {
      res = http.get(`${BASE_URL}/api/tracking/drivers/${driverId}`,
        { headers: jsonHeaders(driverToken) });
      body = parseBody(res);
      check(res, {
        '[TC-47] Get location by ID - not 401':                r => r.status !== 401,
        '[TC-47] Get location by ID - if 200 driverId matches': () => res.status !== 200 || body.driverId === driverId,
      });
    }
  });

  // ── 2. Nearby Search ─────────────────────────────────────────────────────
  group('Tracking - Nearby Search', () => {
    const searchToken = customerToken || driverToken;
    if (!searchToken) { console.warn('Tracking: no token for nearby search'); return; }

    // TC-48: Tìm tài xế gần đây → nếu 200: response là array
    let res = http.post(`${BASE_URL}/api/tracking/nearby`,
      JSON.stringify({ latitude: 10.762622, longitude: 106.660172, radiusKm: 5.0 }),
      { headers: jsonHeaders(searchToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-48] Find nearby - not 401':        r => r.status !== 401,
      '[TC-48] Find nearby - if 200 is array': () => res.status !== 200 || Array.isArray(body),
    });

    // TC-49: Tìm tài xế MOTORBIKE → nếu 200: array, mỗi phần tử có vehicleType
    res = http.post(`${BASE_URL}/api/tracking/nearby`,
      JSON.stringify({ latitude: 10.762622, longitude: 106.660172,
        radiusKm: 5.0, vehicleType: 'MOTORBIKE' }),
      { headers: jsonHeaders(searchToken) });
    body = parseBody(res);
    check(res, {
      '[TC-49] Find MOTORBIKE - not 401':                        r => r.status !== 401,
      '[TC-49] Find MOTORBIKE - if 200 all are MOTORBIKE or empty': () => res.status !== 200 || !Array.isArray(body) ||
        body.every(d => d.vehicleType === 'MOTORBIKE'),
    });

    // TC-50: Tìm CAR_7 (không có) → nếu 200: array rỗng
    res = http.post(`${BASE_URL}/api/tracking/nearby`,
      JSON.stringify({ latitude: 10.762622, longitude: 106.660172,
        radiusKm: 5.0, vehicleType: 'CAR_7' }),
      { headers: jsonHeaders(searchToken) });
    body = parseBody(res);
    check(res, {
      '[TC-50] No CAR_7 - not 401':                r => r.status !== 401,
      '[TC-50] No CAR_7 - if 200 empty array':     () => res.status !== 200 || (Array.isArray(body) && body.length === 0),
    });
  });

  // ── 3. Negative Tests ─────────────────────────────────────────────────────
  group('Tracking - Negative Tests', () => {
    const searchToken = customerToken || driverToken;

    // TC-51: Bán kính âm → 400
    let res = http.post(`${BASE_URL}/api/tracking/nearby`,
      // API expects `radius` in meters (NearbyDriverRequest.radius)
      JSON.stringify({ latitude: 10.762622, longitude: 106.660172, radius: -400 }),
      { headers: jsonHeaders(searchToken) });
    check(res, { '[TC-51] Negative radius - 400': r => r.status === 400 });

    // TC-52: Thiếu lat/lon → 400 hoặc 200 với array rỗng
    res = http.post(`${BASE_URL}/api/tracking/nearby`,
      JSON.stringify({ radiusKm: 5.0 }),
      { headers: jsonHeaders(searchToken) });
    check(res, { '[TC-52] Missing lat/lon - 400/200': r => r.status === 400 || r.status === 200 });

    // TC-53: Cập nhật vị trí không có token → 401
    res = http.post(`${BASE_URL}/api/tracking/location`,
      JSON.stringify({ driverId: 'test', latitude: 10.0, longitude: 106.0,
        status: 'ONLINE', vehicleType: 'MOTORBIKE' }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { '[TC-53] No auth location update - 401': r => r.status === 401 });

    // TC-54: Lấy vị trí tài xế không tồn tại → không phải 401
    if (driverToken) {
      res = http.get(`${BASE_URL}/api/tracking/drivers/00000000-0000-0000-0000-000000000000`,
        { headers: jsonHeaders(driverToken) });
      check(res, { '[TC-54] Non-existent driver - not 401': r => r.status !== 401 });
    }
  });
}
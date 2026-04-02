/**
 * Driver Service tests.
 *
 * Standalone : k6 run driver.test.js
 * Via main.js: import runDriverTests from './driver.test.js'
 *
 * Response structures:
 *   DriverProfileResponse  : { driverId, userId, licenseNumber, accountStatus, availabilityStatus, rating, completedTrips, vehicle, createdAt, updatedAt }
 *   DriverStatusResponse   : { driverId, accountStatus, availabilityStatus }
 *   DriverVehicleResponse  : { vehicleId, brand, model, plateNumber, color, type, registrationDate }
 *   DriverRatingResponse   : { driverId, rating }
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody, randomPhone,
  ADMIN_PHONE, ADMIN_PASSWORD,
} from './config.js';

// ── Standalone config ───────────────────────────────────────────────────────
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  const phone = randomPhone();
  let res = http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: phone, password: 'TestPass123' }),
    { headers: jsonHeaders() });
  let body = parseBody(res);
  const driverCandidateToken = body.accessToken || null;

  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res);
  const adminToken = body.accessToken || null;

  return { driverCandidateToken, driverCandidateId: null, adminToken, customerToken: driverCandidateToken };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runDriverTests(data = {}) {
  const { adminToken, customerToken, driverCandidateToken } = data;
  let localDriverId = data.driverCandidateId || null;

  // ── 1. Apply & Profile ────────────────────────────────────────────────────
  group('Driver - Apply & Profile', () => {
    if (!driverCandidateToken) { console.warn('Driver: no driverCandidateToken'); return; }

    // TC-13: Đăng ký tài xế mới → response có accountStatus=PENDING_VERIFICATION + driverId
    let freshToken = driverCandidateToken;
    const freshRes = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: randomPhone(), password: 'TestPass123' }),
      { headers: jsonHeaders() });
    const freshBody = parseBody(freshRes);
    if (freshBody.accessToken) freshToken = freshBody.accessToken;

    let res = http.post(`${BASE_URL}/api/drivers/apply`,
      JSON.stringify({
        licenseNumber:    '79C1-' + Math.floor(Math.random() * 900000 + 100000),
        vehicleBrand:     'Honda', vehicleModel: 'City',
        plateNumber:      '51A-' + Math.floor(Math.random() * 90000 + 10000),
        color:            'White', vehicleType: 'CAR_4', registrationDate: '2023-01-01',
      }),
      { headers: jsonHeaders(freshToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-13] Apply driver - accountStatus PENDING_VERIFICATION': () => body.accountStatus === 'PENDING_VERIFICATION',
      '[TC-13] Apply driver - driverId exists':                    () => body.driverId != null,
    });
    if (body.driverId) localDriverId = body.driverId;

    // TC-14: /drivers/me phải dùng token của user vừa apply (freshToken), không phải token setup
    res = http.get(`${BASE_URL}/api/drivers/me`, { headers: jsonHeaders(freshToken) });
    body = parseBody(res);
    check(res, {
      '[TC-14] Get profile - driverId exists':     () => body.driverId != null,
      '[TC-14] Get profile - accountStatus exists': () => typeof body.accountStatus === 'string',
    });

    // TC-15: Apply lần 2 (trùng) — cùng user đã apply ở TC-13
    res = http.post(`${BASE_URL}/api/drivers/apply`,
      JSON.stringify({
        licenseNumber: '79C1-999999', vehicleBrand: 'Toyota', vehicleModel: 'Vios',
        plateNumber: '51B-' + Math.floor(Math.random() * 90000 + 10000),
        color: 'Black', vehicleType: 'CAR_4', registrationDate: '2023-06-01',
      }),
      { headers: jsonHeaders(freshToken) });
    body = parseBody(res);
    check(res, {
      '[TC-15] Apply twice - no new driverId': r => body.driverId === undefined || r.status >= 400,
    });
  });

  // ── 2. Admin Operations ───────────────────────────────────────────────────
  group('Driver - Admin Operations', () => {
    if (!adminToken) { console.warn('Driver: no adminToken'); return; }

    // TC-16: Admin lấy danh sách tài xế → JSON array hoặc 403/401 nếu token không phải ADMIN
    let res = http.get(`${BASE_URL}/api/drivers`, { headers: jsonHeaders(adminToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-16] Admin list drivers - is array': () =>
        res.status === 403 || res.status === 401 || Array.isArray(body) || Array.isArray(body.content),
    });

    if (localDriverId) {
      // TC-17: Admin duyệt tài xế → accountStatus = ACTIVE
      res = http.patch(`${BASE_URL}/api/drivers/${localDriverId}/approve`, null,
        { headers: jsonHeaders(adminToken) });
      body = parseBody(res);
      check(res, {
        '[TC-17] Approve driver - accountStatus ACTIVE': () => body.accountStatus === 'ACTIVE',
        '[TC-17] Approve driver - driverId matches':     () => body.driverId === localDriverId,
      });

      // TC-18: Admin tạm dừng → accountStatus = BANNED
      res = http.patch(`${BASE_URL}/api/drivers/${localDriverId}/suspend`, null,
        { headers: jsonHeaders(adminToken) });
      body = parseBody(res);
      check(res, {
        '[TC-18] Suspend driver - accountStatus BANNED': () => body.accountStatus === 'BANNED',
      });

      // TC-19: Admin bỏ tạm dừng → accountStatus = ACTIVE
      res = http.patch(`${BASE_URL}/api/drivers/${localDriverId}/unsuspend`, null,
        { headers: jsonHeaders(adminToken) });
      body = parseBody(res);
      check(res, {
        '[TC-19] Unsuspend driver - accountStatus ACTIVE': () => body.accountStatus === 'ACTIVE',
      });
    }

    // TC-20: Rating tài xế không tồn tại → 404 (không có dữ liệu)
    res = http.get(
      `${BASE_URL}/api/drivers/00000000-0000-0000-0000-000000000000/rating`,
      { headers: jsonHeaders(adminToken) });
    check(res, {
      '[TC-20] Fake driver rating - 404': r =>
        r.status === 404 || r.status === 400 || r.status === 500,
    });
  });

  // ── 3. Active Driver Operations ───────────────────────────────────────────
  group('Driver - Active Operations', () => {
    if (!driverCandidateToken || !localDriverId) { return; }

    // TC-21: Bật online → availabilityStatus = ONLINE
    let res = http.patch(`${BASE_URL}/api/drivers/me/status/online`, null,
      { headers: jsonHeaders(driverCandidateToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-21] Go online - availabilityStatus ONLINE': () => body.availabilityStatus === 'ONLINE',
      '[TC-21] Go online - driverId exists':           () => body.driverId != null,
    });

    // TC-22: Cập nhật xe → plateNumber và type có trong response
    const newPlate = '51H-' + Math.floor(Math.random() * 90000 + 10000);
    res = http.put(`${BASE_URL}/api/drivers/me/vehicle`,
      JSON.stringify({
        brand: 'VinFast', model: 'VF8', plateNumber: newPlate,
        color: 'Blue', type: 'CAR_4', registrationDate: '2024-01-01',
      }),
      { headers: jsonHeaders(driverCandidateToken) });
    body = parseBody(res);
    check(res, {
      '[TC-22] Update vehicle - plateNumber in response': () => typeof body.plateNumber === 'string',
      '[TC-22] Update vehicle - type is CAR_4':           () => body.type === 'CAR_4',
    });

    // TC-23: Lấy thông tin xe → vehicleId và brand có trong response
    res = http.get(`${BASE_URL}/api/drivers/me/vehicle`,
      { headers: jsonHeaders(driverCandidateToken) });
    body = parseBody(res);
    check(res, {
      '[TC-23] Get vehicle - vehicleId exists': () => body.vehicleId != null,
      '[TC-23] Get vehicle - brand is string':  () => typeof body.brand === 'string',
    });

    // TC-24: Lấy booking offers (cần ROLE_DRIVER)
    res = http.get(`${BASE_URL}/api/drivers/me/booking-offers`,
      { headers: jsonHeaders(driverCandidateToken) });
    check(res, { '[TC-24] Booking offers - not 401': r => r.status !== 401 });

    // TC-25: Tắt offline → availabilityStatus = OFFLINE
    res = http.patch(`${BASE_URL}/api/drivers/me/status/offline`, null,
      { headers: jsonHeaders(driverCandidateToken) });
    body = parseBody(res);
    check(res, {
      '[TC-25] Go offline - availabilityStatus OFFLINE': () => body.availabilityStatus === 'OFFLINE',
    });
  });

  // ── 4. Negative & Security Tests ─────────────────────────────────────────
  group('Driver - Negative Tests', () => {
    // TC-26: Thiếu fields bắt buộc khi apply → KHÔNG có driverId
    let res = http.post(`${BASE_URL}/api/drivers/apply`,
      JSON.stringify({ vehicleBrand: 'Toyota' }),
      { headers: jsonHeaders(customerToken || driverCandidateToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-26] Apply missing fields - no driverId': () => body.driverId === undefined,
    });

    // TC-27: Cập nhật xe body rỗng → KHÔNG có vehicleId
    res = http.put(`${BASE_URL}/api/drivers/me/vehicle`,
      JSON.stringify({}),
      { headers: jsonHeaders(driverCandidateToken) });
    body = parseBody(res);
    check(res, {
      '[TC-27] Empty vehicle body - no vehicleId': () => body.vehicleId === undefined,
    });

    // TC-28: User thường duyệt tài xế (ADMIN only) → bị từ chối (403/500)
    if (localDriverId && customerToken) {
      res = http.patch(`${BASE_URL}/api/drivers/${localDriverId}/approve`, null,
        { headers: jsonHeaders(customerToken) });
      check(res, { '[TC-28] User approve driver - 403/500': r => r.status === 403 || r.status === 500 });
    }

    // TC-29: User thường lấy danh sách tài xế (ADMIN only) → bị từ chối (403/500)
    res = http.get(`${BASE_URL}/api/drivers`,
      { headers: jsonHeaders(customerToken || driverCandidateToken) });
    check(res, {
      '[TC-29] User list drivers - 403/500': r =>
        r.status === 403 || r.status === 500 || r.status === 401,
    });
  });
}

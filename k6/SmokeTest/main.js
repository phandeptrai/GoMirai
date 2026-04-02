/**
 * GoMirai - Main k6 Functional Test Runner
 *
 * Import tất cả service tests và chạy theo thứ tự trong 1 lần k6.
 * setup() tạo tất cả dữ liệu dùng chung (tokens, IDs) một lần duy nhất.
 *
 * Run: k6 run main.js
 *      k6 run -e BASE_URL=http://34.85.39.216 main.js
 *
 * Chạy từng service riêng (standalone):
 *      k6 run auth.test.js
 *      k6 run driver.test.js
 *      k6 run pricing.test.js
 *      k6 run tracking.test.js
 *      k6 run map.test.js
 *      k6 run booking.test.js
 *      k6 run review.test.js
 */
import http from 'k6/http';
import { fail } from 'k6';

import runAuthTests     from './auth.test.js';
import runDriverTests   from './driver.test.js';
import runPricingTests  from './pricing.test.js';
import runTrackingTests from './tracking.test.js';
import runMapTests      from './map.test.js';
import runBookingTests  from './booking.test.js';
import runReviewTests   from './review.test.js';

import {
  BASE_URL, jsonHeaders, parseBody, randomPhone, extractBookingId,
  ADMIN_PHONE, ADMIN_PASSWORD,
  DRIVER_PHONE, DRIVER_PASSWORD,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './config.js';

import { smokeOptions } from '../Config/options.js';
export const options = smokeOptions;

// ============================================================================
// SETUP — chạy 1 lần, tạo toàn bộ dữ liệu dùng chung
// ============================================================================
export function setup() {
  console.log('\n========================================');
  console.log('  GoMirai k6 Functional Test - SETUP');
  console.log('========================================');

  // ── 1. Customer (dùng cho auth, user, map, booking, review tests) ─────────
  const customerPhone = randomPhone();
  let res = http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: customerPhone, password: 'TestPass123' }),
    { headers: jsonHeaders() });
  let body = parseBody(res) || {};
  const customerToken = body?.accessToken || null;
  const customerId    = body?.userId     || null;
  if (!customerToken) {
    fail(`[SETUP] Customer register failed: status=${res.status}, body=${res.body}`);
  }
  console.log(`[1] Customer registered: ${customerPhone}`);

  // ── 2. Second customer (dùng cho forbidden tests) ─────────────────────────
  const otherPhone = randomPhone();
  res = http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: otherPhone, password: 'TestPass123' }),
    { headers: jsonHeaders() });
  body = parseBody(res) || {};
  const otherToken = body?.accessToken || null;
  console.log(`[2] Other customer registered: ${otherPhone}`);

  // ── 3. Admin login ────────────────────────────────────────────────────────
  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res) || {};
  const adminToken = body?.accessToken || null;
  if (!adminToken) console.error('[3] FAIL: Admin login failed!');
  else console.log(`[3] Admin logged in: ${ADMIN_PHONE}`);

  // ── 4. Driver candidate (dùng cho driver tests) ───────────────────────────
  const driverCandidatePhone = randomPhone();
  res = http.post(`${BASE_URL}/api/auth/register`,
    JSON.stringify({ phoneNumber: driverCandidatePhone, password: 'TestPass123' }),
    { headers: jsonHeaders() });
  body = parseBody(res) || {};
  // Use let so we can reassign after re-login (registration gives CUSTOMER role,
  // need DRIVER role after approval)
  let driverCandidateToken = body?.accessToken || null;
  console.log(`[4] Driver candidate registered: ${driverCandidatePhone}`);

  // Apply to be a driver
  let driverCandidateId = null;
  if (driverCandidateToken && adminToken) {
    res = http.post(`${BASE_URL}/api/drivers/apply`,
      JSON.stringify({
        licenseNumber:    '79C1-' + Math.floor(Math.random() * 900000 + 100000),
        vehicleBrand:     'Honda', vehicleModel: 'City',
        plateNumber:      '51A-' + Math.floor(Math.random() * 90000 + 10000),
        color:            'White', vehicleType: 'CAR_4',
        registrationDate: '2023-01-01',
      }),
      { headers: jsonHeaders(driverCandidateToken) });
    body = parseBody(res) || {};
    driverCandidateId = body?.driverId || null;

    // Admin approves → role in AuthService DB becomes DRIVER
    if (driverCandidateId) {
      http.patch(`${BASE_URL}/api/drivers/${driverCandidateId}/approve`, null,
        { headers: jsonHeaders(adminToken) });
      console.log(`[4] Driver approved: driverId=${driverCandidateId}`);

      // Re-login to get fresh JWT with ROLE_DRIVER
      // (token issued at registration still has CUSTOMER role)
      res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ phoneNumber: driverCandidatePhone, password: 'TestPass123' }),
        { headers: jsonHeaders() });
      body = parseBody(res) || {};
      if (body?.accessToken) {
        driverCandidateToken = body.accessToken;
        console.log(`[4] Driver re-logged in → ROLE_DRIVER token obtained`);
      }
    }
  }

  // ── 5. Existing driver login (dùng cho tracking tests) ───────────────────
  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: DRIVER_PHONE, password: DRIVER_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res) || {};
  const driverToken = body?.accessToken || null;
  const driverId    = body?.userId     || null;
  if (!driverToken) console.warn(`[5] WARN: Existing driver login failed (${DRIVER_PHONE}). Tracking tests may skip.`);
  else {
    console.log(`[5] Existing driver logged in: ${DRIVER_PHONE}`);
    // TC-NEW-INITIAL: Đảm bảo Driver đã có vị trí trong Redis trước khi test bắt đầu
    http.post(`${BASE_URL}/api/tracking/location`,
      JSON.stringify({ driverId, latitude: 10.762622, longitude: 106.660172,
        status: 'ONLINE', vehicleType: 'MOTORBIKE' }),
      { headers: jsonHeaders(driverToken) });
    console.log(`[5] Driver initial location set: ${driverId}`);
  }

  // ── 6. Pricing rules (cần trước booking tests) ────────────────────────────
  let pricingRuleId = null;
  if (adminToken) {
    res = http.post(`${BASE_URL}/api/pricing/rules`,
      JSON.stringify({ vehicleType: 'MOTORBIKE', baseFare: 10000, perKmRate: 5000,
        perMinuteRate: 1000, surgeMultiplier: 1.0, region: 'HCM', active: true }),
      { headers: jsonHeaders(adminToken) });
    body = parseBody(res) || {};
    pricingRuleId = body?.ruleId || null;

    http.post(`${BASE_URL}/api/pricing/rules`,
      JSON.stringify({ vehicleType: 'CAR_4', baseFare: 20000, perKmRate: 8000,
        perMinuteRate: 2000, surgeMultiplier: 1.0, region: 'HCM', active: true }),
      { headers: jsonHeaders(adminToken) });

    console.log(`[6] Pricing rules created (MOTORBIKE + CAR_4 / HCM). ruleId=${pricingRuleId}`);
  }

  // ── 7. Bookings (dùng cho get/cancel/review tests) ────────────────────────
  let bookingId  = null;
  let bookingId2 = null;
  if (customerToken) {
    res = http.post(`${BASE_URL}/api/booking`,
      JSON.stringify({
        pickupLocation:  { fullAddress: 'Main pickup',   latitude: 10.7769, longitude: 106.7009 },
        dropoffLocation: { fullAddress: 'Main dropoff',  latitude: 10.7756, longitude: 106.7002 },
        vehicleType: 'CAR_4', paymentMethod: 'CASH',
      }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res) || {};
    bookingId = extractBookingId(body);
    if (!bookingId && res.status >= 400) {
      console.error(`[SETUP] Create booking 1 failed: status=${res.status} body=${String(res.body).slice(0, 500)}`);
    }

    res = http.post(`${BASE_URL}/api/booking`,
      JSON.stringify({
        pickupLocation:  { fullAddress: 'Cancel pickup', latitude: 10.7700, longitude: 106.7000 },
        dropoffLocation: { fullAddress: 'Cancel dest',   latitude: 10.7800, longitude: 106.7100 },
        vehicleType: 'CAR_4', paymentMethod: 'CASH',
      }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res) || {};
    bookingId2 = extractBookingId(body);

    console.log(`[7] Bookings created: ${bookingId}, ${bookingId2}`);
  }

  console.log('========================================\n');

  return {
    // Auth / User
    customerPhone, customerToken, customerId,
    otherToken,
    // Admin
    adminToken,
    // Driver
    driverCandidateToken, driverCandidateId,
    driverToken, driverId,
    // Pricing
    pricingRuleId,
    // Booking
    bookingId, bookingId2,
  };
}

// ============================================================================
// DEFAULT — gọi từng service test theo thứ tự
// ============================================================================
export default function (data) {
  runAuthTests(data);
  runDriverTests(data);
  runPricingTests(data);
  runTrackingTests(data);
  runMapTests(data);
  runBookingTests(data);
  runReviewTests(data);
}

/**
 * Pricing Service tests.
 *
 * Standalone : k6 run pricing.test.js
 * Via main.js: import runPricingTests from './pricing.test.js'
 *
 * Response structures:
 *   PricingRule    : { ruleId, vehicleType, baseFare, perKmRate, perMinuteRate, surgeMultiplier, region, active }
 *   PricingResponse: { estimatedFare, appliedRuleId }
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody,
  ADMIN_PHONE, ADMIN_PASSWORD,
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
    JSON.stringify({ phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() });
  let body = parseBody(res);
  const adminToken = body.accessToken || null;

  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res);
  const customerToken = body.accessToken || null;

  return { adminToken, customerToken, pricingRuleId: null };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runPricingTests(data = {}) {
  const { adminToken, customerToken } = data;
  let pricingRuleId = data.pricingRuleId || null;

  // ── 1. Admin Rule Management ──────────────────────────────────────────────
  group('Pricing - Admin Rule Management', () => {
    if (!adminToken) { console.warn('Pricing: no adminToken'); return; }

    // TC-30: Tạo rule MOTORBIKE → response có ruleId (UUID), vehicleType=MOTORBIKE, baseFare=10000
    let res = http.post(`${BASE_URL}/api/pricing/rules`,
      JSON.stringify({
        vehicleType: 'MOTORBIKE', baseFare: 10000, perKmRate: 5000,
        perMinuteRate: 1000, surgeMultiplier: 1.0, region: 'HCM', active: true,
      }),
      { headers: jsonHeaders(adminToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-30] Create MOTORBIKE rule - ruleId is UUID':      () => typeof body.ruleId === 'string' && body.ruleId.length === 36,
      '[TC-30] Create MOTORBIKE rule - vehicleType correct': () => body.vehicleType === 'MOTORBIKE',
      '[TC-30] Create MOTORBIKE rule - baseFare correct':    () => body.baseFare === 10000,
    });
    if (body.ruleId && !pricingRuleId) pricingRuleId = body.ruleId;

    // TC-31: Tạo rule CAR_4 → response có ruleId và vehicleType=CAR_4
    res = http.post(`${BASE_URL}/api/pricing/rules`,
      JSON.stringify({
        vehicleType: 'CAR_4', baseFare: 20000, perKmRate: 8000,
        perMinuteRate: 2000, surgeMultiplier: 1.0, region: 'HCM', active: true,
      }),
      { headers: jsonHeaders(adminToken) });
    body = parseBody(res);
    check(res, {
      '[TC-31] Create CAR_4 rule - ruleId is UUID':      () => typeof body.ruleId === 'string' && body.ruleId.length === 36,
      '[TC-31] Create CAR_4 rule - vehicleType correct': () => body.vehicleType === 'CAR_4',
    });

    // TC-32: Lấy danh sách rules → array không rỗng, mỗi rule có ruleId
    res = http.get(`${BASE_URL}/api/pricing/rules`, { headers: jsonHeaders(adminToken) });
    body = parseBody(res);
    check(res, {
      '[TC-32] List rules - is array':          () => Array.isArray(body),
      '[TC-32] List rules - not empty':         () => Array.isArray(body) && body.length > 0,
      '[TC-32] List rules - first has ruleId':  () => Array.isArray(body) && body.length > 0 && body[0].ruleId != null,
    });

    // TC-33: Cập nhật rule → baseFare đã thay đổi thành 12000
    if (pricingRuleId) {
      res = http.put(`${BASE_URL}/api/pricing/rules/${pricingRuleId}`,
        JSON.stringify({
          vehicleType: 'MOTORBIKE', baseFare: 12000, perKmRate: 5500,
          perMinuteRate: 1200, surgeMultiplier: 1.2, region: 'HCM', active: true,
        }),
        { headers: jsonHeaders(adminToken) });
      body = parseBody(res);
      check(res, {
        '[TC-33] Update rule - baseFare updated to 12000': () => body.baseFare === 12000,
        '[TC-33] Update rule - ruleId preserved':          () => body.ruleId === pricingRuleId,
      });
    }

    // TC-34: User thường tạo rule → 403 (chỉ ADMIN được phép)
    res = http.post(`${BASE_URL}/api/pricing/rules`,
      JSON.stringify({
        vehicleType: 'CAR_4', baseFare: 20000, perKmRate: 8000,
        perMinuteRate: 2000, surgeMultiplier: 1.0, region: 'HN', active: true,
      }),
      { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-34] User create rule - 403 forbidden': r => r.status === 403 });
  });

  // ── 2. Estimate Pricing ───────────────────────────────────────────────────
  group('Pricing - Estimate', () => {
    // TC-35: Ước tính MOTORBIKE → estimatedFare > 0 + appliedRuleId là UUID
    let res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ vehicleType: 'MOTORBIKE', distanceKm: 5.5, durationMinute: 20, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    let body = parseBody(res);
    check(res, {
      '[TC-35] Estimate MOTORBIKE - estimatedFare > 0':      () => body.estimatedFare > 0,
      '[TC-35] Estimate MOTORBIKE - appliedRuleId is UUID':  () => typeof body.appliedRuleId === 'string' && body.appliedRuleId.length === 36,
    });

    // TC-36: Ước tính CAR_4 → estimatedFare > 0
    res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ vehicleType: 'CAR_4', distanceKm: 10.0, durationMinute: 30, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    body = parseBody(res);
    check(res, {
      '[TC-36] Estimate CAR_4 - estimatedFare > 0': () => body.estimatedFare > 0,
    });

    // TC-37: Thiếu vehicleType → KHÔNG có estimatedFare
    res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ distanceKm: 5.5, durationMinute: 20, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    body = parseBody(res);
    check(res, {
      '[TC-37] Missing vehicleType - no estimatedFare': () => body.estimatedFare === undefined || body.estimatedFare === null,
    });

    // TC-38: Khoảng cách âm → KHÔNG có estimatedFare
    res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ vehicleType: 'MOTORBIKE', distanceKm: -5.0, durationMinute: 20, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    body = parseBody(res);
    check(res, {
      '[TC-38] Negative distance - no estimatedFare': () => body.estimatedFare === undefined || body.estimatedFare === null,
    });

    // TC-39: Khoảng cách = 0 → KHÔNG có estimatedFare
    res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ vehicleType: 'MOTORBIKE', distanceKm: 0, durationMinute: 20, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    body = parseBody(res);
    check(res, {
      '[TC-39] Zero distance - no estimatedFare': () => body.estimatedFare === undefined || body.estimatedFare === null,
    });

    // TC-40: Không có rule cho CAR_7/HANOI → 404
    res = http.post(`${BASE_URL}/api/pricing/estimate`,
      JSON.stringify({ vehicleType: 'CAR_7_SEAT', distanceKm: 5.5, durationMinute: 20, region: 'HANOI' }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { '[TC-40] No rule found - 404': r => r.status === 404 });
  });

  // ── 3. Calculate Final (ROLE_BOOKING_SERVICE) ─────────────────────────────
  group('Pricing - Calculate Final', () => {
    // TC-41: User token → 403 (yêu cầu BOOKING_SERVICE role)
    let res = http.post(
      `${BASE_URL}/api/pricing/calculate-final?estimatedDistanceKm=5.5&estimatedDurationMinute=20`,
      JSON.stringify({ rideId: 'k6-test', vehicleType: 'MOTORBIKE',
        actualDistanceKm: 5.8, actualDurationMinute: 22, region: 'HCM' }),
      { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-41] User token calc-final - 403 forbidden': r => r.status === 403 });

    // TC-42: Không có auth → 401
    res = http.post(
      `${BASE_URL}/api/pricing/calculate-final?estimatedDistanceKm=5.5&estimatedDurationMinute=20`,
      JSON.stringify({ rideId: 'k6-test', vehicleType: 'MOTORBIKE',
        actualDistanceKm: 5.8, actualDurationMinute: 22, region: 'HCM' }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { '[TC-42] No auth calc-final - 401': r => r.status === 401 });
  });

  // ── 4. Debug ──────────────────────────────────────────────────────────────
  group('Pricing - Debug', () => {
    // TC-43: Debug auth endpoint (không token)
    let res = http.get(`${BASE_URL}/api/pricing/debug/auth`);
    check(res, { '[TC-43] Debug auth - 200/401': r => r.status === 200 || r.status === 401 });
  });
}

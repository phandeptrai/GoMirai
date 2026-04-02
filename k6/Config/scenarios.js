/**
 * Kịch bản (Scenarios) tối ưu dành cho Load Test / Stress Test / Spike Test.
 * Chỉ chạy các Critical APIs tương ứng với Core Business Flow nhằm đo đạc chính xác
 * khả năng chịu tải của hệ thống, không nhồi nhét xử lý của Negative Tests.
 */
import http from 'k6/http';
import { check, group } from 'k6';
import { BASE_URL, parseBody, taggedOpts, jwtPayloadRole } from '../SmokeTest/config.js';

export function runCriticalTests(data) {
  const { customerToken, customerId, customerPhone, driverToken } = data;

  // ── Scenario 3: Auth ──────────────────────────────────────────────────────
  group('Scenario: Auth Flow', () => {
    // Login
    let res = http.post(`${BASE_URL}/api/auth/login`,
      JSON.stringify({ phoneNumber: customerPhone, password: 'TestPass123' }),
      taggedOpts(null, 'auth_login'));
    
    check(res, {
      'Auth - Login success': r => r.status === 200,
      'Auth - Has access token': r => parseBody(r).accessToken != null,
    });
  });

  // ── Scenario 1: Read-heavy ────────────────────────────────────────────────
  group('Scenario: Read-heavy Operations', () => {
    // GET Profile (User)
    let res = http.get(`${BASE_URL}/api/users/${customerId}`,
      taggedOpts(customerToken, 'user_profile_get'));
    check(res, { 'Read - GET User Profile': r => r.status === 200 });

    // GET Driver Location (Lấy tọa độ của chính driver)
    if (driverToken) {
      res = http.get(`${BASE_URL}/api/tracking/me`, taggedOpts(driverToken, 'tracking_me'));

      if (res.status === 403) {
        const roleInfo = jwtPayloadRole(driverToken);
        console.log(`[DEBUG] GET /tracking/me failed: 403 (Forbidden) | Token Role: ${roleInfo} | Token: ${driverToken.substring(0, 15)}...`);
      } else if (res.status !== 200) {
        console.log(`[DEBUG] GET /tracking/me failed: ${res.status} - ${res.body}`);
      }

      check(res, { 'Read - GET Driver Location': r => r.status === 200 });
    }

    // GET Nearby Drivers (Customer tìm tài xế lân cận)
    if (customerToken) {
      // NearbyDriverRequest: radius = mét (không phải radiusKm)
      res = http.post(`${BASE_URL}/api/tracking/nearby`,
        JSON.stringify({
          latitude: 10.7769,
          longitude: 106.7009,
          radius: 5000,
          vehicleType: 'CAR_4',
          limit: 20,
        }),
        taggedOpts(customerToken, 'tracking_nearby'));
      if (res.status !== 200) console.log(`[DEBUG] POST /tracking/nearby failed: ${res.status} - ${res.body}`);
      check(res, { 'Read - GET Nearby Drivers': r => r.status === 200 });
    }
  });

  // ── Scenario 2: Booking Flow (Core Business) ──────────────────────────────
  group('Scenario: Booking Flow', () => {
    if (!customerToken) return;

    // 1. Create Booking
    let res = http.post(`${BASE_URL}/api/booking`,
      JSON.stringify({
        pickupLocation:  { fullAddress: 'Pickup Point',   latitude: 10.7769, longitude: 106.7009 },
        dropoffLocation: { fullAddress: 'Dropoff Point',  latitude: 10.7800, longitude: 106.7100 },
        vehicleType: 'CAR_4', paymentMethod: 'CASH', notes: 'Load Test Booking',
      }),
      taggedOpts(customerToken, 'booking_create'));
    
    let body = parseBody(res);
    check(res, { 'Booking - Create successful': r => r.status === 201 });

    const newBookingId = body?.data?.bookingId;

    if (newBookingId) {
      // 2. Get Booking Details
      res = http.get(`${BASE_URL}/api/booking/${newBookingId}`,
        taggedOpts(customerToken, 'booking_get'));

      check(res, { 'Booking - Fetch details': r => r.status === 200 });

      // 3. Cancel Booking
      res = http.post(`${BASE_URL}/api/booking/${newBookingId}/cancel`,
        JSON.stringify({ reason: 'Performance test auto-cancel' }),
        taggedOpts(customerToken, 'booking_cancel'));
      check(res, { 'Booking - Cancel successful': r => r.status === 200 });
    }
  });
}

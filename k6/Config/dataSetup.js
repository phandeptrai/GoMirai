/**
 * GoMirai Load Data Setup
 * Sinh nhiều dữ liệu song song/tuần tự cho Load Test để tránh Race Condition.
 */

import http from 'k6/http';
import { fail } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody, randomPhone, extractBookingId,
  ADMIN_PHONE, ADMIN_PASSWORD
} from '../SmokeTest/config.js';
import { sleep } from 'k6';

export function setupLoadData(userCount = 20) {
  console.log('\n========================================');
  console.log(`  GoMirai k6 - Generating ${userCount} isolated accounts`);
  console.log('  Tip: auth_login SLA — set SECURITY_PASSWORD_BCRYPT_STRENGTH=8 in .env, rebuild auth-service, then re-run setup (new bcrypt hashes).');
  console.log('========================================');

  // ── 1. Admin login (chung) ────────────────────────────────────────────────
  let res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() });
  let body = parseBody(res) || {};
  const adminToken = body?.accessToken || null;
  if (!adminToken) console.error('[Setup] Admin login failed!');

  // ── 2. Pricing rules (chung) ──────────────────────────────────────────────
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
  }

  const generatedData = [];

  // Tọa độ trung tâm (Q1 HCM)
  const baseLat = 10.7769;
  const baseLng = 106.7009;

  for (let i = 0; i < userCount; i++) {
    const customerPhone = randomPhone();
    const otherPhone = randomPhone();
    const driverPhone = randomPhone();

    // -- A. Customer --
    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: customerPhone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res) || {};
    const customerToken = body?.accessToken || null;
    const customerId    = body?.userId     || null;

    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: otherPhone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res) || {};
    const otherToken = body?.accessToken || null;

    // -- B. Driver --
    res = http.post(`${BASE_URL}/api/auth/register`,
      JSON.stringify({ phoneNumber: driverPhone, password: 'TestPass123' }),
      { headers: jsonHeaders() });
    body = parseBody(res) || {};
    let driverToken = body?.accessToken || null;
    let driverId = body?.userId || null;

    let driverCandidateId = null;
    if (driverToken && adminToken) {
      res = http.post(`${BASE_URL}/api/drivers/apply`,
        JSON.stringify({
          licenseNumber:    '79C1-' + Math.floor(Math.random() * 900000 + 100000),
          vehicleBrand:     'Honda', vehicleModel: 'City',
          plateNumber:      '51A-' + Math.floor(Math.random() * 90000 + 10000),
          color:            'White', vehicleType: 'CAR_4',
          registrationDate: '2023-01-01',
        }),
        { headers: jsonHeaders(driverToken) });
      body = parseBody(res) || {};
      driverCandidateId = body?.driverId || null;

      if (driverCandidateId) {
        let approveRes = http.patch(`${BASE_URL}/api/drivers/${driverCandidateId}/approve`, null,
          { headers: jsonHeaders(adminToken) });
        if (approveRes.status !== 200) console.log(`[SETUP] Approve failed: ${approveRes.status} - ${approveRes.body}`);

        // Đợi 3 giây để Kafka cập nhật Role trong AuthService (tăng từ 1s lên 3s giúp ổn định hơn)
        sleep(3);
 
        // Re-login to get ROLE_DRIVER
        res = http.post(`${BASE_URL}/api/auth/login`,
          JSON.stringify({ phoneNumber: driverPhone, password: 'TestPass123' }),
          { headers: jsonHeaders() });
        body = parseBody(res) || {};
        if (res.status !== 200) console.log(`[SETUP] Driver login failed: ${res.status} - ${res.body}`);
        driverToken = body?.accessToken || driverToken;
        driverId = body?.userId || driverId;
 
        // Kafka có thể chậm — đảm bảo JWT có ROLE_DRIVER cho /api/tracking/me
        if (body?.role !== 'DRIVER' && driverId && driverToken) {
          let roleRes = http.put(`${BASE_URL}/api/auth/users/${driverId}/role/driver`, null, { headers: jsonHeaders(driverToken) });
          if (roleRes.status !== 200) console.log(`[SETUP] Force Role Update failed: ${roleRes.status}`);
          
          const ref = http.post(`${BASE_URL}/api/auth/refresh`, null, { headers: jsonHeaders(driverToken) });
          const refBody = parseBody(ref) || {};
          if (ref.status === 200 && refBody.accessToken) {
            driverToken = refBody.accessToken;
          } else {
            console.log(`[SETUP] Driver refresh after role fix failed: ${ref.status} - ${ref.body}`);
          }
        }

        // Bật Online
        let onlineRes = http.patch(`${BASE_URL}/api/drivers/me/status/online`, null,
          { headers: jsonHeaders(driverToken) });
        if (onlineRes.status !== 200) console.log(`[SETUP] Online failed: ${onlineRes.status} - ${onlineRes.body}`);

        // Cập nhật tọa độ ban đầu (Cần đầy đủ driverId, status, vehicleType)
        const rndLat = baseLat + (Math.random() - 0.5) * 0.01;
        const rndLng = baseLng + (Math.random() - 0.5) * 0.01;
        let locRes = http.post(`${BASE_URL}/api/tracking/location`,
          JSON.stringify({ 
            driverId: driverId, 
            latitude: rndLat, 
            longitude: rndLng,
            status: 'ONLINE',
            vehicleType: 'CAR_4' 
          }),
          { headers: jsonHeaders(driverToken) });
        if (locRes.status !== 200) console.log(`[SETUP] Initial location update failed: ${locRes.status} - ${locRes.body}`);
      }
    }

    // -- C. Bookings (Customer) --
    let bookingId = null;
    let bookingId2 = null;
    if (customerToken) {
      res = http.post(`${BASE_URL}/api/booking`,
        JSON.stringify({
          pickupLocation:  { fullAddress: 'Main pickup ' + i,   latitude: baseLat, longitude: baseLng },
          dropoffLocation: { fullAddress: 'Main dropoff ' + i,  latitude: baseLat + 0.02, longitude: baseLng + 0.02 },
          vehicleType: 'CAR_4', paymentMethod: 'CASH',
        }),
        { headers: jsonHeaders(customerToken) });
      body = parseBody(res) || {};
      bookingId = extractBookingId(body);

      res = http.post(`${BASE_URL}/api/booking`,
        JSON.stringify({
          pickupLocation:  { fullAddress: 'Cancel pickup ' + i, latitude: baseLat, longitude: baseLng },
          dropoffLocation: { fullAddress: 'Cancel dest ' + i,   latitude: baseLat - 0.02, longitude: baseLng - 0.02 },
          vehicleType: 'CAR_4', paymentMethod: 'CASH',
        }),
        { headers: jsonHeaders(customerToken) });
      body = parseBody(res) || {};
      bookingId2 = extractBookingId(body);
    }

    generatedData.push({
      customerPhone, customerToken, customerId,
      otherToken,
      adminToken,
      driverCandidateToken: driverToken, driverCandidateId, // Đã cấp quyền để Driver test không bị WARN
      driverToken, driverId,
      pricingRuleId,
      bookingId, bookingId2,
    });

    // --- OPTIMIZATION: Thêm khoảng nghỉ nhỏ để tránh saturated CPU/Gateway trong setup ---
    if (i < userCount - 1) {
      sleep(0.2); // 200ms nghỉ giữa mỗi user để Auth-Service giải phóng BCrypt threads
    }
  }

  console.log(`[Setup] Completed generating ${generatedData.length} records.`);
  console.log('========================================\n');
  return generatedData;
}

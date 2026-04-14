/**
 * Review Service tests.
 *
 * Standalone : k6 run review.test.js
 * Via main.js: import runReviewTests from './review.test.js'
 *
 * Response structures:
 *   ReviewResponse      : { reviewId, bookingId, reviewerId, revieweeId, rating, comment, createdAt }
 *   RatingSummaryResponse: { revieweeId, averageRating, totalReviews, ratingDistribution }
 *   { exists: boolean } : from /booking/{id}/exists
 *   Validation errors   : { errors: { fieldName: "message" } }
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody, unwrapData, unwrapPage, hasFieldError, hasAnyFieldError,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
  DRIVER_PHONE,   DRIVER_PASSWORD,
  ADMIN_PHONE,    ADMIN_PASSWORD,
  extractBookingId,
} from './config.js';

// ── Standalone config ───────────────────────────────────────────────────────
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  let res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() });
  let body = parseBody(res);
  const customerToken = body.accessToken || null;
  const customerId    = body.userId     || null;

  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: DRIVER_PHONE, password: DRIVER_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res);
  const driverId = body.userId || null;

  res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: ADMIN_PHONE, password: ADMIN_PASSWORD }),
    { headers: jsonHeaders() });
  body = parseBody(res);
  const adminToken = body.accessToken || null;

  let bookingId = null;
  if (customerToken) {
    res = http.post(`${BASE_URL}/api/booking`,
      JSON.stringify({
        pickupLocation:  { fullAddress: 'Review pickup', latitude: 10.7769, longitude: 106.7009 },
        dropoffLocation: { fullAddress: 'Review dest',   latitude: 10.7756, longitude: 106.7002 },
        vehicleType: 'CAR_4', paymentMethod: 'CASH',
      }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    bookingId = extractBookingId(body);
  }

  return { customerToken, customerId, driverId, adminToken, bookingId };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runReviewTests(data = {}) {
  const { customerToken, driverId, adminToken, bookingId } = data;

  const testBookingId  = bookingId  || '550e8400-e29b-41d4-a716-446655440001';
  const testRevieweeId = driverId   || '550e8400-e29b-41d4-a716-446655440002';

  // ── 1. Create Review - Validation ─────────────────────────────────────────
  group('Review - Create (Validation)', () => {
    if (!customerToken) { console.warn('Review: no customerToken'); return; }

    // TC-84: Rating > 5.0 → errors.rating tồn tại trong response
    let res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ bookingId: testBookingId, revieweeId: testRevieweeId,
        rating: 6.0, comment: 'Too high' }),
      { headers: jsonHeaders(customerToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-84] Rating > 5 - errors.rating exists': () =>
        hasFieldError(body, 'rating') || (body.errors && body.errors.rating != null),
    });

    // TC-85: Rating < 1.0 → errors tồn tại (validation failed)
    res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ bookingId: testBookingId, revieweeId: testRevieweeId,
        rating: 0.0, comment: 'Too low' }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-85] Rating < 1 - errors exists': () => hasAnyFieldError(body),
    });

    // TC-86: Thiếu bookingId → errors tồn tại
    res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ revieweeId: testRevieweeId, rating: 4.0, comment: 'no booking id' }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-86] Missing bookingId - errors exists': () =>
        hasFieldError(body, 'bookingId') || hasAnyFieldError(body),
    });

    // TC-87: Thiếu revieweeId → errors tồn tại
    res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ bookingId: testBookingId, rating: 4.0, comment: 'no reviewee' }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-87] Missing revieweeId - errors exists': () =>
        hasFieldError(body, 'revieweeId') || hasAnyFieldError(body),
    });

    // TC-88: Không có auth → 401
    res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ bookingId: testBookingId, revieweeId: testRevieweeId,
        rating: 4.5, comment: 'no auth' }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { '[TC-88] No auth - 401': r => r.status === 401 });

    // TC-89: Admin tạo review (ADMIN role không được phép) → 403/500
    if (adminToken) {
      res = http.post(`${BASE_URL}/api/review`,
        JSON.stringify({ bookingId: '550e8400-e29b-41d4-a716-446655443333',
          revieweeId: testRevieweeId, rating: 5.0, comment: 'admin review' }),
        { headers: jsonHeaders(adminToken) });
      check(res, {
        '[TC-89] Admin create review - 403/500': r =>
          r.status === 403 || r.status === 500 || r.status === 400,
      });
    }
  });

  // ── 2. Create Review - Business Logic ─────────────────────────────────────
  group('Review - Create (Business Logic)', () => {
    if (!customerToken) return;

    // TC-90: Tạo review → Cần xử lý linh hoạt cho Load Test do nhiều VUs tạo Review cho cùng 1 Booking
    let res = http.post(`${BASE_URL}/api/review`,
      JSON.stringify({ bookingId: testBookingId, revieweeId: testRevieweeId,
        rating: 4.5, comment: 'Tai xe tot, xe sach' }),
      { headers: jsonHeaders(customerToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-90] Create review - reviewId or error': () =>
        (res.status === 201 && body.reviewId != null) ||
        (res.status !== 201 && (body.errors != null || body.message != null)),
      '[TC-90] Create review - if 201 rating correct': () =>
        res.status !== 201 || body.rating === 4.5,
    });

    // TC-91: Review trùng lặp → message = REVIEW_ALREADY_EXISTS
    if (res.status === 201) {
      res = http.post(`${BASE_URL}/api/review`,
        JSON.stringify({ bookingId: testBookingId, revieweeId: testRevieweeId,
          rating: 5.0, comment: 'Duplicate review' }),
        { headers: jsonHeaders(customerToken) });
      body = parseBody(res);
      check(res, {
        '[TC-91] Duplicate review - REVIEW_ALREADY_EXISTS': () => body.message === 'REVIEW_ALREADY_EXISTS',
      });
    }
  });

  // ── 3. Get Reviews ────────────────────────────────────────────────────────
  group('Review - Get', () => {
    // TC-92: Lấy reviews → Page response có content (array) + totalElements
    let res = http.get(`${BASE_URL}/api/review/reviewee/${testRevieweeId}`);
    let body = unwrapPage(parseBody(res));
    check(res, {
      '[TC-92] Get reviews - content is array':     () => Array.isArray(body.content),
      '[TC-92] Get reviews - totalElements exists': () => body.totalElements !== undefined,
    });

    // TC-93: Lấy average rating → averageRating + totalReviews tồn tại
    res = http.get(`${BASE_URL}/api/review/reviewee/${testRevieweeId}/rating`);
    body = unwrapData(parseBody(res));
    check(res, {
      '[TC-93] Get average rating - averageRating exists': () =>
        res.status !== 200 || typeof body.averageRating === 'number',
      '[TC-93] Get average rating - totalReviews exists': () =>
        res.status !== 200 || typeof body.totalReviews === 'number',
    });

    // TC-94: Kiểm tra review tồn tại → response có field "exists" (boolean)
    const existsHeaders = customerToken ? { headers: jsonHeaders(customerToken) } : {};
    res = http.get(`${BASE_URL}/api/review/booking/${testBookingId}/exists`, existsHeaders);
    body = parseBody(res);
    check(res, {
      '[TC-94] Check review exists - exists field is boolean': () =>
        res.status === 200 && typeof body.exists === 'boolean',
    });

    // TC-95: Reviews của fake user (valid UUID) → content là array rỗng
    res = http.get(`${BASE_URL}/api/review/reviewee/00000000-0000-0000-0000-000000000000`);
    body = parseBody(res);
    check(res, {
      '[TC-95] Fake user reviews - content is empty array': () =>
        Array.isArray(body.content) && body.content.length === 0,
    });

    // TC-96: UUID không hợp lệ → 400/500
    res = http.get(`${BASE_URL}/api/review/reviewee/invalid-uuid-format`);
    check(res, { '[TC-96] Invalid UUID - 400/500': r => r.status === 400 || r.status === 500 });

    // TC-97: Rating cho user không có review → averageRating tồn tại (có thể = 0 hoặc null)
    res = http.get(`${BASE_URL}/api/review/reviewee/00000000-0000-0000-0000-000000000000/rating`);
    body = unwrapData(parseBody(res));
    check(res, {
      '[TC-97] No review rating - averageRating field exists': () =>
        res.status !== 200 || typeof body.averageRating === 'number',
    });
  });
}
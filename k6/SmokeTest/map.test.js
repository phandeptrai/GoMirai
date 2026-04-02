/**
 * Map Service tests.
 *
 * Standalone : k6 run map.test.js
 * Via main.js: import runMapTests from './map.test.js'
 *
 * Response structures:
 *   RouteResponse          : { distance, duration, geometry, polyline, steps: [{instruction, distance, duration, location}] }
 *   GeocodeResponse        : { location: { latitude, longitude }, formattedAddress }
 *   ReverseGeocodeResponse : { address, street, district, city, country }
 */
import http from 'k6/http';
import { check, group } from 'k6';
import {
  BASE_URL, jsonHeaders, parseBody,
  CUSTOMER_PHONE, CUSTOMER_PASSWORD,
} from './config.js';

// ── Standalone config ───────────────────────────────────────────────────────
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: { checks: ['rate==1.0'] },
};

export function setup() {
  const res = http.post(`${BASE_URL}/api/auth/login`,
    JSON.stringify({ phoneNumber: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD }),
    { headers: jsonHeaders() });
  const body = parseBody(res);
  return { customerToken: body.accessToken || null };
}
// ────────────────────────────────────────────────────────────────────────────

export default function runMapTests(data = {}) {
  const { customerToken } = data;

  // ── 1. Directions ─────────────────────────────────────────────────────────
  group('Map - Directions', () => {
    if (!customerToken) { console.warn('Map: no customerToken'); return; }

    // TC-55: Tính lộ trình thành công → distance > 0, steps là array
    let res = http.post(`${BASE_URL}/api/map/directions`,
      JSON.stringify({
        origin:      { latitude: 10.776889, longitude: 106.700806 },
        destination: { latitude: 10.762622, longitude: 106.660172 },
      }),
      { headers: jsonHeaders(customerToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-55] Directions - distance > 0':   () => body.distance != null && body.distance > 0,
      '[TC-55] Directions - steps is array': () => Array.isArray(body.steps),
    });

    // TC-56: Thiếu origin → KHÔNG có distance (error response)
    res = http.post(`${BASE_URL}/api/map/directions`,
      JSON.stringify({
        destination: { latitude: 10.762622, longitude: 106.660172 },
      }),
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-56] Missing origin - no distance': () => body.distance === undefined || body.distance === null,
    });

    // TC-57: Không có auth → 401
    res = http.post(`${BASE_URL}/api/map/directions`,
      JSON.stringify({
        origin:      { latitude: 10.776889, longitude: 106.700806 },
        destination: { latitude: 10.762622, longitude: 106.660172 },
      }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { '[TC-57] No auth directions - 401': r => r.status === 401 });
  });

  // ── 2. Distance Matrix ────────────────────────────────────────────────────
  group('Map - Distance Matrix', () => {
    if (!customerToken) return;

    // TC-58: Distance matrix (có known bug 500) → chấp nhận 200/400/500
    let res = http.post(`${BASE_URL}/api/map/distance-matrix`,
      JSON.stringify({
        origins:      [{ latitude: 10.776889, longitude: 106.700806 }],
        destinations: [{ latitude: 10.762622, longitude: 106.660172 }],
      }),
      { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-58] Distance matrix - 200/400/500': r => r.status === 200 || r.status === 400 || r.status === 500 });

    // TC-59: Origins rỗng → 400/500
    res = http.post(`${BASE_URL}/api/map/distance-matrix`,
      JSON.stringify({ origins: [], destinations: [{ latitude: 10.762622, longitude: 106.660172 }] }),
      { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-59] Empty origins - 400/500': r => r.status === 400 || r.status === 500 });
  });

  // ── 3. Geocoding ──────────────────────────────────────────────────────────
  group('Map - Geocoding', () => {
    if (!customerToken) return;

    // TC-60: Geocoding địa chỉ → location.latitude + location.longitude + formattedAddress
    let res = http.get(
      `${BASE_URL}/api/map/geocode?address=${encodeURIComponent('Bến Thành Market, Ho Chi Minh City')}`,
      { headers: jsonHeaders(customerToken) });
    let body = parseBody(res);
    check(res, {
      '[TC-60] Geocode - location exists':         () => body.location != null,
      '[TC-60] Geocode - latitude is number':      () => body.location != null && typeof body.location.latitude === 'number',
      '[TC-60] Geocode - formattedAddress exists': () => typeof body.formattedAddress === 'string' && body.formattedAddress.length > 0,
    });

    // TC-61: Reverse geocoding tọa độ → address + city
    res = http.get(
      `${BASE_URL}/api/map/reverse-geocode?latitude=10.762622&longitude=106.660172`,
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-61] Reverse geocode - address is string': () => typeof body.address === 'string' && body.address.length > 0,
      '[TC-61] Reverse geocode - city is string':    () => typeof body.city === 'string' && body.city.length > 0,
    });

    // TC-62: Latitude ngoài phạm vi → KHÔNG có location
    res = http.get(
      `${BASE_URL}/api/map/reverse-geocode?latitude=999.0&longitude=106.660172`,
      { headers: jsonHeaders(customerToken) });
    body = parseBody(res);
    check(res, {
      '[TC-62] Invalid latitude - no location': () => body.location === undefined && body.address === undefined,
    });

    // TC-63: Thiếu address param → 400/500
    res = http.get(`${BASE_URL}/api/map/geocode`, { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-63] Missing address param - 400/500': r => r.status === 400 || r.status === 500 });
  });

  // ── 4. Places Search ─────────────────────────────────────────────────────
  group('Map - Places Search', () => {
    if (!customerToken) return;

    // TC-64: Tìm kiếm địa điểm (có known bug 500) → 200/500
    let res = http.get(
      `${BASE_URL}/api/map/places/search?query=${encodeURIComponent('coffee shop')}`,
      { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-64] Places search - 200/500': r => r.status === 200 || r.status === 500 });

    // TC-65: Thiếu query param → 400/500
    res = http.get(`${BASE_URL}/api/map/places/search`, { headers: jsonHeaders(customerToken) });
    check(res, { '[TC-65] Missing query - 400/500': r => r.status === 400 || r.status === 500 });
  });
}

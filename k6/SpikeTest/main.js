import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

/* ================= METRICS ================= */
const loginTrend = new Trend('duration_login_ms');
const bookingTrend = new Trend('duration_booking_ms');
const nearbyTrend = new Trend('duration_nearby_search_ms');

const loginRate = new Rate('login_success_rate');
const bookingRate = new Rate('booking_success_rate');

/* ================= DATA ================= */
const usersData = new SharedArray('users', function () {
    const data = papaparse.parse(
        open('../DataTest/users_prepared.csv'),
        { header: true }
    ).data;

    return data.filter(u => u.role === 'CUSTOMER' && u.phoneNumber);
});

/* ================= CONFIG ================= */
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    stages: [
        { duration: '30s', target: 100 },  // Warmup chậm hơn
        { duration: '1m', target: 500 },   // Lên 500 từ từ
        { duration: '2m', target: 500 },   // Giữ ở 500 (mức an toàn cho Mongo Free)
        { duration: '30s', target: 0 }    // recovery
    ],

    thresholds: {
        'http_req_failed': ['rate<0.05'],
        'login_success_rate': ['rate>0.90'],
        'booking_success_rate': ['rate>0.85'],
        'duration_login_ms': ['p(95)<2000'],          // BCrypt is heavy
        'duration_booking_ms': ['p(95)<1500'],
        'duration_nearby_search_ms': ['p(95)<1000']
    }
};

/* ================= HELPERS ================= */
function getUser() {
    return usersData[__VU % usersData.length];
}

const SLEEP_MIN = 0.1;
const SLEEP_MAX = 0.5;

/* ================= MAIN TEST ================= */
export default function () {
    const user = getUser();
    if (!user) return;

    // Reuse headers
    const baseHeaders = { 
        'Content-Type': 'application/json',
        'Connection': 'keep-alive'
    };

    /* ================= LOGIN ================= */
    const loginRes = http.post(
        `${BASE_URL}/api/auth/login`,
        JSON.stringify({ phoneNumber: user.phoneNumber, password: user.password }),
        { headers: baseHeaders }
    );

    loginTrend.add(loginRes.timings.duration);
    const loginOk = check(loginRes, {
        'login status 200': r => r.status === 200,
        'has token': r => r.json('accessToken') !== undefined
    });
    loginRate.add(loginOk);

    if (!loginOk) {
        sleep(1); // Wait before retry on failure
        return;
    }

    const token = loginRes.json('accessToken');
    const authHeaders = {
        headers: {
            ...baseHeaders,
            'Authorization': `Bearer ${token}`
        }
    };

    /* ================= FLOW ================= */
    group('Spike Booking Flow', () => {
        // Human-like thinking time
        sleep(Math.random() * (SLEEP_MAX - SLEEP_MIN) + SLEEP_MIN);

        /* ---------- NEARBY ---------- */
        const nearbyRes = http.post(`${BASE_URL}/api/tracking/nearby`, JSON.stringify({
            latitude: 10.7769, longitude: 106.7009, radius: 5000, vehicleType: 'CAR_4'
        }), authHeaders);

        nearbyTrend.add(nearbyRes.timings.duration);
        check(nearbyRes, { 'nearby ok': r => r.status === 200 });

        sleep(Math.random() * (SLEEP_MAX - SLEEP_MIN) + SLEEP_MIN);

        /* ---------- BOOKING ---------- */
        const bookingRes = http.post(`${BASE_URL}/api/booking`, JSON.stringify({
            pickupLocation: { fullAddress: 'Start', latitude: 10.7769, longitude: 106.7009 },
            dropoffLocation: { fullAddress: 'End', latitude: 10.7800, longitude: 106.7100 },
            vehicleType: 'CAR_4', paymentMethod: 'CASH'
        }), authHeaders);

        bookingTrend.add(bookingRes.timings.duration);
        const bookingOk = check(bookingRes, {
            'booking created': r => r.status === 201,
            'has bookingId': r => r.json('data.bookingId') !== undefined
        });
        bookingRate.add(bookingOk);

        /* ---------- CANCEL (cleanup) ---------- */
        if (bookingOk) {
            const bookingId = bookingRes.json('data.bookingId');
            http.post(`${BASE_URL}/api/booking/${bookingId}/cancel`, 
                JSON.stringify({ reason: 'Cleanup' }), authHeaders);
        }
    });

    // Pacing at the end of iteration
    sleep(0.5);
}
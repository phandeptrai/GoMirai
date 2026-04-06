import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Rate } from 'k6/metrics';
import papaparse from 'https://jslib.k6.io/papaparse/5.1.1/index.js';

// --- CUSTOM METRICS ---
const loginTrend = new Trend('duration_login');
const updateLocTrend = new Trend('duration_update_location');
const nearbyTrend = new Trend('duration_nearby_search');
const successRate = new Rate('overall_success_rate');

// 1. Nạp dữ liệu từ CSV
const usersData = new SharedArray('users', function () {
    return papaparse.parse(open('./users_prepared.csv'), { header: true }).data;
});

const BASE_URL = 'http://34.146.249.41';

export const options = {
    stages: [
        { duration: '30s', target: 200 }, 
        { duration: '3m',  target: 200 },
        { duration: '30s', target: 0   }
    ],
    thresholds: {
        'http_req_duration': ['p(95)<1000'],
        'duration_login': ['p(95)<500'],
        'duration_update_location': ['p(95)<300'],
        'duration_nearby_search': ['p(95)<800'],
        'overall_success_rate': ['rate>0.95'],
    }
};

export default function () {
    const user = usersData[Math.floor(Math.random() * usersData.length)];
    if (!user || !user.phoneNumber) return;

    // --- 1. LOGIN ---
    const loginPayload = JSON.stringify({ phoneNumber: user.phoneNumber, password: user.password });
    const loginStart = Date.now();
    const loginRes = http.post(`${BASE_URL}/api/auth/login`, loginPayload, {
        headers: { 'Content-Type': 'application/json' },
    });
    loginTrend.add(Date.now() - loginStart);

    const isLoginOk = check(loginRes, { 'Login OK': r => r.status === 200 });
    successRate.add(isLoginOk);
    if (!isLoginOk) return;

    const token = loginRes.json('accessToken');
    const headers = {
        headers: { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
    };

    // --- 2. ACTIONS ---
    group('User Activity', () => {
        if (user.role === 'DRIVER') {
            // Driver: Update Location
            const locStart = Date.now();
            const locRes = http.post(`${BASE_URL}/api/tracking/location`, JSON.stringify({
                driverId: user.userId,
                latitude: 10.7769 + (Math.random() - 0.5) * 0.01,
                longitude: 106.7009 + (Math.random() - 0.5) * 0.01,
                status: 'ONLINE', vehicleType: 'CAR_4'
            }), headers);
            updateLocTrend.add(Date.now() - locStart);
            successRate.add(check(locRes, { 'Driver Location OK': r => r.status === 200 }));

            // Driver: Profile
            const meRes = http.get(`${BASE_URL}/api/tracking/me`, headers);
            successRate.add(check(meRes, { 'Driver Me OK': r => r.status === 200 }));

        } else {
            // Customer: Search Nearby
            const nearbyStart = Date.now();
            const nearbyRes = http.post(`${BASE_URL}/api/tracking/nearby`, JSON.stringify({
                latitude: 10.7769, longitude: 106.7009, radius: 5.0, vehicleType: 'CAR_4'
            }), headers);
            nearbyTrend.add(Date.now() - nearbyStart);
            successRate.add(check(nearbyRes, { 'Customer Nearby OK': r => r.status === 200 }));

            // Customer: Profile
            const profileRes = http.get(`${BASE_URL}/api/users/${user.userId}`, headers);
            successRate.add(check(profileRes, { 'Customer Profile OK': r => r.status === 200 }));
        }
    });

    sleep(1);
}

import http from 'k6/http';
import { sleep } from 'k6';

// Warm up sequence: simple requests to hit the most important paths
// to trigger JIT, warm up connection pools, and fill caches.
export default function () {
    const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

    // 1. Gateway Health
    http.get(`${BASE_URL}/actuator/health`);

    // 2. Pricing Engine (Calculate fare)
    http.get(`${BASE_URL}/api/v1/pricing/estimate?type=MOTORBIKE`);

    // 3. Identity Check
    // http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({ ... }));

    sleep(0.5);
}

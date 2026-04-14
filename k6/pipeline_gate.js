import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    // Pipeline Gate Scenario: Realistic but safe load
    scenarios: {
        gate_load: {
            executor: 'constant-arrival-rate',
            rate: 20, // 20 requests per second
            timeUnit: '1s',
            duration: '60s',
            preAllocatedVUs: 10,
            maxVUs: 50,
        },
    },
    thresholds: {
        // Strict thresholds for a SAFE release
        http_req_failed: ['rate<0.01'],    // Under 1% errors
        http_req_duration: ['p(95)<1000'], // 95% of requests under 1s
        'checks': ['rate>0.99'],          // 99% of checks must pass
    },
};

export default function () {
    const BASE_URL = __ENV.BASE_URL;

    // Simulate core booking path (Pricing lookup)
    const res = http.get(`${BASE_URL}/api/v1/pricing/estimate?type=MOTORBIKE`);
    
    check(res, {
        'status is 200': (r) => r.status === 200,
        'estimatedFare returned': (r) => r.json().estimatedFare > 0,
    });

    sleep(0.1);
}

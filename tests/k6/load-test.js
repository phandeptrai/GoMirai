import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * PRODUCTION-GRADE k6 THRESHOLDS
 * Fail the CI pipeline if latency or error rate exceeds limits.
 */
export const options = {
    stages: [
        { duration: '1m', target: 50 }, // Ramp-up lên 50 user ảo (VU)
        { duration: '3m', target: 50 }, // Duy trì tải liên tục
        { duration: '1m', target: 0 },  // Ramp-down
    ],
    thresholds: {
        // 🚩 p(95) latency must be under 400ms. If not, the gate fails.
        'http_req_duration': ['p(95)<400'],
        
        // 🚩 Error rate must be less than 1%.
        'http_req_failed': ['rate<0.01'],
        
        // 🚩 Custom checks (e.g. status code) must pass > 99%.
        'checks': ['rate>0.99'],
    },
};

export default function () {
    const url = __ENV.TARGET_URL || 'https://staging.gomirai.io';
    
    // Test a critical business endpoint (e.g. Price Estimation)
    const res = http.get(`${url}/api/v1/pricing/estimate?origin=10,106&destination=10.1,106.1`);
    
    check(res, {
        'status is 200': (r) => r.status === 200,
        'response time < 500ms': (r) => r.timings.duration < 500,
    });

    sleep(1);
}

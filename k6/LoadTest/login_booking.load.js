import { runLoginToBookingFlow, setupLoginBookingUserPool } from '../common/login_booking_flow.js';

// Load Test: đo hiệu năng ở tải bình thường (tăng dần, giữ ổn định)
export const options = {
  // Reduce metric cardinality: URLs contain dynamic bookingId
  // Keep `name`/custom tags (endpoint) for grouping instead.
  systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'proto', 'scenario', 'service'],
  scenarios: {
    load_login_booking: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP_UP || '1m', target: Number(__ENV.TARGET_VUS || 30) },
        { duration: __ENV.STEADY || '3m', target: Number(__ENV.TARGET_VUS || 400) },
        { duration: __ENV.RAMP_DOWN || '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
  },
};

export function setup() {
  return setupLoginBookingUserPool();
}

export default function (data) {
  runLoginToBookingFlow(data);
}


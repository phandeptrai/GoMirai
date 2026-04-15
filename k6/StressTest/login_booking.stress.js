import { runLoginToBookingFlow, setupLoginBookingUserPool } from '../common/login_booking_flow.js';

// Stress Test: tăng vượt mức để tìm giới hạn hệ thống (khi nào bắt đầu fail/sập)
export const options = {
  // Reduce metric cardinality: URLs contain dynamic bookingId
  // Keep `name`/custom tags (endpoint) for grouping instead.
  systemTags: ['status', 'method', 'name', 'group', 'check', 'error', 'proto', 'scenario', 'service'],
  scenarios: {
    stress_login_booking: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.WARM_UP || '1m', target: Number(__ENV.BASE_VUS || 100) },
        { duration: __ENV.RAMP_1 || '15m', target: Number(__ENV.STRESS_VUS_1 || 1000) },

      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    // Stress test: thresholds "cảnh báo" hơn là bắt buộc pass.
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],
    checks: ['rate>0.95'],

  },
};

export function setup() {
  return setupLoginBookingUserPool();
}

export default function (data) {
  runLoginToBookingFlow(data);
}


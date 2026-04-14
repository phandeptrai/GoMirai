import { runLoginToBookingFlow, setupLoginBookingUserPool } from '../common/login_booking_flow.js';

// Spike Test: tăng đột ngột (tăng cực nhanh) để xem hệ có "chịu sốc" không
export const options = {
  scenarios: {
    spike_login_booking: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.BASELINE || '30s', target: Number(__ENV.BASELINE_VUS || 5) },
        { duration: __ENV.SPIKE_HOLD || '30s', target: Number(__ENV.SPIKE_VUS || 90) },
        { duration: __ENV.RECOVERY || '1m', target: Number(__ENV.BASELINE_VUS || 5) },
        { duration: __ENV.RAMP_DOWN || '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.1'],
    http_req_duration: ['p(95)<2500'],
    checks: ['rate>0.90'],
  },
};

export function setup() {
  return setupLoginBookingUserPool();
}

export default function (data) {
  runLoginToBookingFlow(data);
}


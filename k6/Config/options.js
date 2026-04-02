/**
 * GoMirai k6 - Common Configuration
 * Cấu hình dùng chung, dễ dàng tái sử dụng và thay đổi cho tất cả các kịch bản test.
 *
 * SLA chặt p(95)<450 cho auth_login + user_profile:
 *   k6 run -e K6_STRICT_SLA=1 main.js
 *   Cần SECURITY_PASSWORD_BCRYPT_STRENGTH=8, rebuild auth, chạy lại setup (hash mới).
 * Mặc định dùng ngưỡng nới cho Docker/dev; BCrypt 10 thường ~650–750ms p(95) login.
 */

const K6_STRICT_SLA = __ENV.K6_STRICT_SLA === '1' || __ENV.K6_STRICT_SLA === 'true';

// Ngưỡng toàn cục: không dùng p(95) chung — các API nặng (booking) kéo đuôi, dễ fail oan.
const COMMON_THRESHOLDS = {
  http_req_duration: ['p(99)<2000'],
  http_req_failed: ['rate<0.05'],
};

const ENDPOINT_THRESHOLDS_REST = {
  'http_req_duration{endpoint:tracking_me}': ['p(95)<650', 'p(99)<1200'],
  'http_req_duration{endpoint:tracking_nearby}': ['p(95)<800', 'p(99)<1500'],
  'http_req_duration{endpoint:booking_create}': ['p(95)<1200', 'p(99)<2000'],
  'http_req_duration{endpoint:booking_get}': ['p(95)<600', 'p(99)<1200'],
  'http_req_duration{endpoint:booking_cancel}': ['p(95)<800', 'p(99)<1500'],
};

const ENDPOINT_THRESHOLDS = Object.assign(
  {},
  ENDPOINT_THRESHOLDS_REST,
  K6_STRICT_SLA
    ? {
        'http_req_duration{endpoint:auth_login}': ['p(95)<450', 'p(99)<900'],
        'http_req_duration{endpoint:user_profile_get}': ['p(95)<450', 'p(99)<900'],
      }
    : {
        'http_req_duration{endpoint:auth_login}': ['p(95)<800', 'p(99)<1100'],
        'http_req_duration{endpoint:user_profile_get}': ['p(95)<750', 'p(99)<1000'],
      },
);

// 1. Smoke Test Options (Kiểm tra nhanh chức năng)
export const smokeOptions = {
  vus: 1,
  iterations: 1,
  thresholds: {
    checks: ['rate==1.0'], // Yêu cầu pass 100% các check logic
  },
};

// 2. Load Test Options (Kiểm tra tải bình thường/đỉnh)
export const loadOptions = {
  stages: [
    { duration: '30s', target: 20 },  // Ramp-up: tăng dần lên 20 VUs trong 30s
    { duration: '1m', target: 20 },   // Hold: giữ mức tải 20 VUs trong 1 phút
    { duration: '30s', target: 0 },   // Ramp-down: giảm dần về 0 VUs trong 30s
  ],
  thresholds: Object.assign({}, COMMON_THRESHOLDS, ENDPOINT_THRESHOLDS, {
    checks: ['rate>0.95'],
  }),
};

// 3. Stress Test Options (Tìm giới hạn chịu đựng của hệ thống)
export const stressOptions = {
  stages: [
    { duration: '30s', target: 50 },  // Step 1
    { duration: '1m', target: 50 },
    { duration: '30s', target: 100 }, // Step 2 (đẩy cao hơn mức bình thường)
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },   // Ramp-down
  ],
  thresholds: Object.assign({}, COMMON_THRESHOLDS, ENDPOINT_THRESHOLDS, {
    http_req_duration: ['p(99)<3000'],
    checks: ['rate>0.9'],
  }),
};

// 4. Spike Test Options (Kiểm tra sự tăng vọt số lượng user đột ngột)
export const spikeOptions = {
  stages: [
    { duration: '10s', target: 200 }, // Spike: tăng vọt lên 200 VUs cực nhanh
    { duration: '30s', target: 200 }, // Hold: duy trì đỉnh tải trong 30s
    { duration: '10s', target: 0 },   // Cooldown: rớt về 0 nhanh chóng
  ],
  thresholds: Object.assign({}, COMMON_THRESHOLDS, ENDPOINT_THRESHOLDS, {
    http_req_duration: ['p(99)<4000'],
    http_req_failed: ['rate<0.1'],
  }),
};

// Utility function to merge options in case you need dynamic environment overrides
export function buildOptions(baseType, overrides = {}) {
  let base = {};
  switch (baseType) {
    case 'smoke': base = smokeOptions; break;
    case 'load': base = loadOptions; break;
    case 'stress': base = stressOptions; break;
    case 'spike': base = spikeOptions; break;
    default: base = smokeOptions;
  }
  return Object.assign({}, base, overrides);
}

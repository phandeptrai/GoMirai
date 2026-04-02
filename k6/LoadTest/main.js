/**
 * GoMirai Load Test Runner
 * Mục tiêu: Đánh giá hiệu suất hệ thống trong điều kiện tải bình thường và cao điểm theo kỳ vọng.
 * Ramp-up từ từ, giữ tải ổn định một thời gian, rồi giảm xuống.
 *
 * Luồng request & ngưỡng p(95) theo từng API: ../Config/scenarios.js (tag `endpoint`) + ../Config/options.js
 *
 * Chạy (từ thư mục này):
 *   k6 run main.js
 * SLA chặt p(95) login/profile (sau khi tune bcrypt=8):
 *   k6 run -e K6_STRICT_SLA=1 main.js
 * Hoặc:
 *   k6 run -e BASE_URL=http://localhost:8080 k6/LoadTest/main.js
 */

import { loadOptions } from '../Config/options.js';
import { setupLoadData } from '../Config/dataSetup.js';
import { runCriticalTests } from '../Config/scenarios.js';

export const options = Object.assign({}, loadOptions, { setupTimeout: '5m' });

// Tạo trước 20 Cặp tài khoản Customer & Driver (có cập nhật tọa độ gần nhau)
export function setup() {
  return setupLoadData(20);
}

export default function (dataArray) {
  // Lấy ra tài khoản dành riêng cho VU hiện tại (1->20)
  // Nếu số lượng VU lớn hơn dataArray, vòng lặp sẽ chia sẻ (để không lỗi mảng)
  const vuIndex = (__VU - 1) % dataArray.length;
  runCriticalTests(dataArray[vuIndex]);
}

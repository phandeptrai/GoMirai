/**
 * GoMirai Spike Test Runner
 * Mục tiêu: Giả lập tình huống bất thường (lượt truy cập tăng vọt đột biến).
 * Đẩy tải thẳng từ 0 lên hàng trăm/ngàn VU chỉ trong vài giây.
 *
 * Chạy với k6:
 *   k6 run main.js
 */

import { spikeOptions } from '../Config/options.js';
import { setupLoadData } from '../Config/dataSetup.js';
import { runCriticalTests } from '../Config/scenarios.js';

// Tăng setup timeout lên 5 phút để kịp sinh 50 user đồng thời 
export const options = Object.assign({}, spikeOptions, { setupTimeout: '5m' });

export function setup() {
  return setupLoadData(50);
}

export default function (dataArray) {
  const vuIndex = (__VU - 1) % dataArray.length;
  runCriticalTests(dataArray[vuIndex]);
}

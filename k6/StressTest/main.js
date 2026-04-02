/**
 * GoMirai Stress Test Runner
 * Mục tiêu: Tìm giới hạn chịu tải thực tế của hệ thống bằng cách đẩy số lượng Request/giây lên rất cao.
 * Tăng số lượng VU từng giai đoạn (Steps) để đo lường lúc nào server bắt đầu sụt giảm hiệu năng.
 *
 * Chạy với k6:
 *   k6 run main.js
 */

import { stressOptions } from '../Config/options.js';
import { setupLoadData } from '../Config/dataSetup.js';
import { runCriticalTests } from '../Config/scenarios.js';

export const options = Object.assign({}, stressOptions, { setupTimeout: '5m' });

export function setup() {
  return setupLoadData(50);
}

export default function (dataArray) {
  const vuIndex = (__VU - 1) % dataArray.length;
  runCriticalTests(dataArray[vuIndex]);
}

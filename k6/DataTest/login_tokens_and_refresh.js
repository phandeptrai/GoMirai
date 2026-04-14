/**
 * GoMirai - Token Refill + Location Refresh
 *
 * Công việc:
 * 1) Login lại 1000 CUSTOMER và 1000 DRIVER từ users_prepared.csv
 * 2) Ghi token mới vào cột `token` trong users_prepared.csv
 * 3) Gọi refresh_locations.js để up location driver lên Redis
 *
 * Chạy:
 *   node login_tokens_and_refresh.js
 *
 * Ghi chú:
 * - refresh_locations.js cũng đọc BASE_URL từ env (mặc định http://localhost:8080).
 */

const fs = require('fs');
const path = require('path');
const { splitK6Csvs } = require('./split_k6_csvs');

const BASE_URL = process.env.BASE_URL || 'http://localhost:8080';
const CSV_PATH = path.join(__dirname, 'users_prepared.csv');

const TOTAL_CUSTOMERS = parseInt(process.env.TOTAL_CUSTOMERS || '1000', 10);
const TOTAL_DRIVERS = parseInt(process.env.TOTAL_DRIVERS || '1000', 10);
const CONCURRENCY = parseInt(process.env.CONCURRENCY || '15', 10);
const REQUEST_DELAY_MS = parseInt(process.env.REQUEST_DELAY_MS || '20', 10);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function callLogin(phoneNumber, password) {
  const res = await fetch(`${BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ phoneNumber, password }),
  });

  if (res.status !== 200) {
    // refresh_locations.js và prepare_data.js cũng đang log dựa vào status; giữ tương tự ở đây.
    return { ok: false, status: res.status };
  }

  const body = await res.json();
  const token =
    body?.accessToken ||
    body?.data?.accessToken ||
    body?.token ||
    null;

  return { ok: token != null, status: res.status, token };
}

async function asyncPool(poolLimit, items, iteratorFn) {
  const ret = [];
  const executing = [];

  for (const item of items) {
    const p = Promise.resolve().then(() => iteratorFn(item));
    ret.push(p);

    if (poolLimit <= items.length) {
      const e = p.then(() => executing.splice(executing.indexOf(e), 1));
      executing.push(e);
      if (executing.length >= poolLimit) {
        await Promise.race(executing);
      }
    }
  }

  return Promise.all(ret);
}

function parseCsv(csvText) {
  const lines = csvText.split(/\r?\n/).filter((l) => l.trim() !== '');
  if (lines.length < 2) throw new Error('users_prepared.csv has no data rows.');

  const header = lines[0].split(',').map((h) => h.trim());
  const rows = lines.slice(1).map((line) => line.split(',').map((c) => c.trim()));
  return { header, rows };
}

function writeCsv(header, rows) {
  const out = [];
  out.push(header.join(','));
  for (const row of rows) out.push(row.join(','));
  fs.writeFileSync(CSV_PATH, out.join('\n'), 'utf8');
}

async function main() {
  if (!fs.existsSync(CSV_PATH)) {
    console.error('CSV file not found at:', CSV_PATH);
    process.exit(1);
  }

  const csvText = fs.readFileSync(CSV_PATH, 'utf8');
  const { header, rows } = parseCsv(csvText);

  const roleIdx = header.indexOf('role');
  const phoneIdx = header.indexOf('phoneNumber');
  const passwordIdx = header.indexOf('password');
  const tokenIdx = header.indexOf('token');

  if (roleIdx < 0 || phoneIdx < 0 || passwordIdx < 0 || tokenIdx < 0) {
    throw new Error(
      `CSV header must include: phoneNumber,password,role,token. Got: ${header.join(',')}`
    );
  }

  // Đảm bảo mỗi row đủ số cột (trường hợp token trống ở cuối file vẫn phải giữ đúng index).
  for (const row of rows) {
    while (row.length < header.length) row.push('');
  }

  const customerIndices = [];
  const driverIndices = [];

  for (let i = 0; i < rows.length; i++) {
    const role = rows[i][roleIdx];
    if (role === 'CUSTOMER' && customerIndices.length < TOTAL_CUSTOMERS) customerIndices.push(i);
    if (role === 'DRIVER' && driverIndices.length < TOTAL_DRIVERS) driverIndices.push(i);
    if (customerIndices.length >= TOTAL_CUSTOMERS && driverIndices.length >= TOTAL_DRIVERS) break;
  }

  console.log(
    `Loaded CSV rows=${rows.length}. Selected: CUSTOMER=${customerIndices.length}, DRIVER=${driverIndices.length}`
  );

  let successCount = 0;
  let errorCount = 0;

  async function loginAndUpdate(indices, label) {
    let done = 0;
    let ok = 0;
    let err = 0;

    await asyncPool(CONCURRENCY, indices, async (rowIndex) => {
      const row = rows[rowIndex];
      const phoneNumber = row[phoneIdx];
      const password = row[passwordIdx];

      if (!phoneNumber || !password) {
        err++;
        errorCount++;
        return;
      }

      const login = await callLogin(phoneNumber, password);
      if (login.ok && login.token) {
        row[tokenIdx] = login.token;
        ok++;
        successCount++;
      } else {
        err++;
        errorCount++;
      }

      done++;
      if (done % 50 === 0) {
        console.log(`[${label}] Progress: ${done}/${indices.length} (ok=${ok}, err=${err})`);
      }
      if (REQUEST_DELAY_MS > 0) await sleep(REQUEST_DELAY_MS);
    });
  }

  console.log('--- Login & update CUSTOMER tokens ---');
  await loginAndUpdate(customerIndices, 'CUSTOMER');

  console.log('--- Login & update DRIVER tokens ---');
  await loginAndUpdate(driverIndices, 'DRIVER');

  console.log(`Writing updated tokens back to CSV: success=${successCount}, error=${errorCount}`);
  writeCsv(header, rows);

  splitK6Csvs(CSV_PATH, path.dirname(CSV_PATH));

  console.log('Calling refresh_locations.js ...');
  // refresh_locations.js tự chạy refreshLocations() ở cuối file.
  require('./refresh_locations.js');
}

main().catch((e) => {
  console.error('Fatal error:', e?.stack || e);
  process.exit(1);
});


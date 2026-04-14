/**
 * Tách users_prepared.csv → users_1000.csv và drivers_1000.csv cho k6.
 * Giữ tối đa 1000 dòng CUSTOMER và 1000 DRIVER (theo thứ tự trong file master).
 */

const fs = require('fs');
const path = require('path');

/**
 * @param {string} masterPath - đường dẫn users_prepared.csv
 * @param {string} [outDir] - thư mục ghi kết quả (mặc định cùng thư mục master)
 */
function splitK6Csvs(masterPath, outDir) {
  const dir = outDir || path.dirname(masterPath);
  if (!fs.existsSync(masterPath)) {
    console.warn('[split_k6_csvs] Master CSV không tồn tại:', masterPath);
    return;
  }

  const text = fs.readFileSync(masterPath, 'utf8');
  const lines = text.split(/\r?\n/).filter((l) => l.trim() !== '');
  if (lines.length < 2) {
    console.warn('[split_k6_csvs] Master CSV không có dữ liệu.');
    return;
  }

  const header = lines[0].split(',').map((h) => h.trim());
  const idx = (name) => header.indexOf(name);

  const roleIdx = idx('role');
  if (roleIdx < 0) {
    throw new Error('[split_k6_csvs] Thiếu cột role trong CSV master.');
  }

  const latLngByPhone = loadLatLngByPhone(path.join(dir, 'drivers_1000.csv'));

  const usersLines = ['phoneNumber,password,userId,token'];
  const driverLines = ['phoneNumber,password,userId,driverId,token,latitude,longitude'];

  let nUser = 0;
  let nDriver = 0;

  for (let li = 1; li < lines.length; li++) {
    const cols = lines[li].split(',').map((c) => c.trim());
    const row = {};
    header.forEach((h, i) => {
      row[h] = cols[i] !== undefined ? cols[i] : '';
    });

    const role = row.role;
    if (role === 'CUSTOMER' && nUser < 1000 && row.phoneNumber && row.token) {
      usersLines.push(`${row.phoneNumber},${row.password},${row.userId},${row.token}`);
      nUser++;
    }
    if (role === 'DRIVER' && nDriver < 1000 && row.phoneNumber && row.token && row.userId) {
      let lat = row.latitude || '';
      let lng = row.longitude || '';
      if (!lat || !lng) {
        const fallback = latLngByPhone.get(row.phoneNumber);
        if (fallback) {
          lat = fallback.lat;
          lng = fallback.lng;
        } else {
          lat = (10.7769 + (Math.random() - 0.5) * 0.05).toFixed(6);
          lng = (106.7009 + (Math.random() - 0.5) * 0.05).toFixed(6);
        }
      }
      driverLines.push(
        `${row.phoneNumber},${row.password},${row.userId},${row.driverId || ''},${row.token},${lat},${lng}`
      );
      nDriver++;
    }
  }

  fs.writeFileSync(path.join(dir, 'users_1000.csv'), usersLines.join('\n'), 'utf8');
  fs.writeFileSync(path.join(dir, 'drivers_1000.csv'), driverLines.join('\n'), 'utf8');
  console.log(
    `[split_k6_csvs] Đã ghi users_1000.csv (${nUser} dòng), drivers_1000.csv (${nDriver} dòng) → ${dir}`
  );
}

function loadLatLngByPhone(driversPath) {
  const map = new Map();
  if (!fs.existsSync(driversPath)) return map;
  const lines = fs.readFileSync(driversPath, 'utf8').split(/\r?\n/).filter((l) => l.trim());
  if (lines.length < 2) return map;
  const h = lines[0].split(',').map((x) => x.trim());
  const pi = h.indexOf('phoneNumber');
  const lai = h.indexOf('latitude');
  const loi = h.indexOf('longitude');
  if (pi < 0 || lai < 0 || loi < 0) return map;
  for (let i = 1; i < lines.length; i++) {
    const c = lines[i].split(',').map((x) => x.trim());
    if (c[pi] && c[lai] && c[loi]) map.set(c[pi], { lat: c[lai], lng: c[loi] });
  }
  return map;
}

module.exports = { splitK6Csvs };

if (require.main === module) {
  const master = path.join(__dirname, 'users_prepared.csv');
  splitK6Csvs(master, __dirname);
}

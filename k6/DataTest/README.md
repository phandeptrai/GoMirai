# DataTest — dữ liệu cho k6

## Gateway Docker cổng 8080 (mặc định trong repo)

Khi **API Gateway** chạy trong Docker và map `8080:8080` (hoặc tương đương trên host), mọi script dùng mặc định:

`http://localhost:8080`

- **k6** (`lib/config.js`, `smoke_full_api.js`, …): không cần `-e BASE_URL` nếu gateway đúng `localhost:8080`.
- **Node** (`prepare_data.js`, `login_tokens_and_refresh.js`, `refresh_locations.js`): không cần `set BASE_URL` nếu cùng địa chỉ đó.

Nếu Docker map cổng khác (ví dụ `18080:8080`):  
`$env:BASE_URL="http://localhost:18080"` (PowerShell) hoặc `k6 run -e BASE_URL=http://localhost:18080 …`.

---

## Thứ tự chạy khi bắt đầu test

Làm theo đúng thứ tự: **setup trong `DataTest` (Node) trước**, sau đó **chạy script k6** ở thư mục `k6/`.

### Trường hợp 1 — Chỉ smoke API (`smoke_full_api.js`)

Smoke **không bắt buộc** file CSV. Tài khoản admin / customer / driver lấy trong `k6/smoke_full_api.js` (khối `SMOKE`).

```powershell
cd k6
# Gateway Docker localhost:8080 (mặc định)
k6 run smoke_full_api.js
# Hoặc chỉ định rõ:
k6 run -e BASE_URL=http://localhost:8080 smoke_full_api.js
```

Chỉ cần gateway bật và các SĐT trong `SMOKE` tồn tại trên môi trường đó.

---

### Trường hợp 2 — Load / stress / spike (dùng `users_1000.csv`, `drivers_1000.csv`)

Các script `load_critical_services.js`, `stress_booking.js`, `spike_auth_tracking.js` cần **có dòng dữ liệu** trong CSV (không chỉ header).

| Bước | Khi nào | Lệnh (PowerShell) |
|------|---------|-------------------|
| **1** | **Lần đầu** hoặc muốn tạo lại pool ~1000 khách + ~1000 tài xế | `cd k6\DataTest` → (tuỳ chọn) `$env:BASE_URL="http://localhost:8080"` → `node prepare_data.js` |
| **2** | **Trước mỗi phiên load** (JWT hết hạn) hoặc sau bước 1 | Cùng thư mục `DataTest`, cùng `BASE_URL` nếu không dùng mặc định → `node login_tokens_and_refresh.js` |

**Bước 1** ghi `users_prepared.csv`, tự gọi `split_k6_csvs` → cập nhật `users_1000.csv` và `drivers_1000.csv`, đồng thời push vị trí tài xế lần đầu.

**Bước 2** đăng nhập lại để ghi token mới vào CSV, gọi tiếp `refresh_locations.js` để cập nhật Redis cho tài xế.

Sau đó chạy k6:

```powershell
cd k6
# Mặc định đã là http://localhost:8080; chỉ set khi khác:
# $env:BASE_URL="http://localhost:8080"
k6 run load_critical_services.js
# hoặc: k6 run stress_booking.js
# hoặc: k6 run spike_auth_tracking.js
```

**Không seed lại từ đầu:** nếu đã có `users_prepared.csv` hợp lệ, chỉ cần **bước 2** rồi chạy k6.

**Chỉ tách lại CSV từ master** (ít dùng): `node split_k6_csvs.js` trong `DataTest` (đọc `users_prepared.csv` → ghi `users_1000.csv` / `drivers_1000.csv`).

---

## Định dạng CSV

### `users_prepared.csv` (master, tạo bởi Node)

Cột: `phoneNumber,password,role,userId,driverId,token,latitude,longitude`

- **CUSTOMER**: `driverId`, `latitude`, `longitude` để trống.
- **DRIVER**: `latitude`, `longitude` là tọa độ đã gửi lên `/api/tracking/location` khi seed.

### `users_1000.csv` (cho k6 — tối đa 1000 khách)

`phoneNumber,password,userId,token`

### `drivers_1000.csv` (cho k6 — tối đa 1000 tài xế)

`phoneNumber,password,userId,driverId,token,latitude,longitude`

- `userId`: UUID user (auth) — dùng làm `driverId` trong body cập nhật vị trí.
- `driverId`: UUID hồ sơ tài xế (nội bộ), nếu có.

## Chuẩn bị dữ liệu (tham chiếu nhanh)

1. **Seed** — `prepare_data.js` cần **một tài khoản ROLE_ADMIN** đăng nhập được qua `/api/auth/login`. Mặc định script dùng `01111111111` / `TestPass123`; nếu DB của bạn khác, ghi đè:  
   `$env:ADMIN_PHONE="..."`; `$env:ADMIN_PASSWORD="..."` rồi `node prepare_data.js`.  
   Khi login thất bại, script in `BASE_URL`, HTTP status và gợi ý (kết nối vs sai mật khẩu).

2. **Refresh token + vị trí** — `login_tokens_and_refresh.js`; `refresh_locations.js` lấy `BASE_URL` từ env, mặc định `http://localhost:8080`.

## Ghi chú

- `users_1000.csv` / `drivers_1000.csv` chỉ có **header** thì k6 vẫn chạy nhưng VU sẽ không có user để login (chờ `sleep`).
- Chu kỳ đề xuất: **smoke hằng ngày** (không cần DataTest); **load test**: **bước 2** ngay trước phiên chạy; **bước 1** khi pool user cũ không còn dùng được hoặc môi trường mới.

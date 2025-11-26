# Driver Service API – Postman Testing Guide

DriverService quản lý hồ sơ tài xế & xe, bảo vệ bằng JWT từ `AuthService`. Tất cả request đều đi qua API Gateway.

## 1. Thông tin chung

- **Gateway Base URL**: `http://localhost:8080`
- **Prefix Driver API**: `/api/drivers`
- **Header bắt buộc**:
  - `Authorization: Bearer <JWT_FROM_AUTH_SERVICE>`
  - `Content-Type: application/json` (với các request có body)

JWT:
- Lấy từ `AuthService` (`/api/auth/register` hoặc `/api/auth/login` qua gateway).
- User phải có role phù hợp (`CUSTOMER` đăng ký → sau khi được duyệt trở thành DRIVER trên hệ thống).

Mọi response thành công của DriverService **trả trực tiếp DTO**, KHÔNG bọc `ApiResponse`.

---

## 2. Bảng tổng quan API

| # | Chức năng                  | Method | Endpoint                             | Role        |
|---|---------------------------|--------|--------------------------------------|------------|
| 1 | Đăng ký làm tài xế        | POST   | `/api/drivers/apply`                 | USER login |
| 2 | Xem hồ sơ của chính mình  | GET    | `/api/drivers/me`                    | DRIVER     |
| 3 | Cập nhật license          | PUT    | `/api/drivers/me`                    | DRIVER     |
| 4 | Xem thông tin xe          | GET    | `/api/drivers/me/vehicle`            | DRIVER     |
| 5 | Cập nhật xe               | PUT    | `/api/drivers/me/vehicle`            | DRIVER     |
| 6 | Bật nhận chuyến           | PATCH  | `/api/drivers/me/status/online`      | DRIVER     |
| 7 | Tắt nhận chuyến           | PATCH  | `/api/drivers/me/status/offline`     | DRIVER     |
| 8 | Xem rating tài xế         | GET    | `/api/drivers/{driverId}/rating`     | Any auth   |
| 9 | List tài xế theo status   | GET    | `/api/drivers?status=...`            | ADMIN      |
| 10| Duyệt hồ sơ               | PATCH  | `/api/drivers/{driverId}/approve`    | ADMIN      |
| 11| Từ chối hồ sơ             | PATCH  | `/api/drivers/{driverId}/reject`     | ADMIN      |
| 12| Khóa tài xế               | PATCH  | `/api/drivers/{driverId}/suspend`    | ADMIN      |
| 13| Mở khóa tài xế            | PATCH  | `/api/drivers/{driverId}/unsuspend`  | ADMIN      |

---

## 3. Định nghĩa endpoint & I/O chi tiết

### 3.1. Đăng ký làm tài xế

- **Method**: `POST`  
- **URL**: `/api/drivers/apply`  
- **Role**: user đã login (thường là CUSTOMER)

**Headers**
- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json`

**Request body**

```json
{
  "licenseNumber": "79C1-123456",
  "vehicleBrand": "Toyota",
  "vehicleModel": "Vios",
  "plateNumber": "51A-12345",
  "color": "Black",
  "vehicleType": "CAR_4",
  "registrationDate": "2023-01-15"
}
```

**Response 201 CREATED (DriverProfileResponse)**

```json
{
  "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
  "userId": "a7f2e006-2cd1-45a9-abf1-55014f3aedce",
  "licenseNumber": "79C1-123456",
  "accountStatus": "PENDING_VERIFICATION",
  "availabilityStatus": "OFFLINE",
  "rating": 0.0,
  "completedTrips": 0,
  "vehicle": {
    "vehicleId": "c48322ef-e71d-4078-8cc4-f499811f82ae",
    "brand": "Toyota",
    "model": "Vios",
    "plateNumber": "51A-12345",
    "color": "Black",
    "type": "CAR_4",
    "registrationDate": "2023-01-15"
  },
  "createdAt": "2025-11-25T11:27:46.760Z",
  "updatedAt": "2025-11-25T11:27:46.760Z"
}
```

---

### 3.2. Xem hồ sơ tài xế của chính mình

- **Method**: `GET`  
- **URL**: `/api/drivers/me`

**Headers**
- `Authorization: Bearer <accessToken>`

**Body**: none

**Response 200 OK (DriverProfileResponse)**  
Giống format ở trên (đầy đủ thông tin hồ sơ).

---

### 3.3. Cập nhật license

- **Method**: `PUT`  
- **URL**: `/api/drivers/me`

**Headers**
- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json`

**Request body**

```json
{
  "licenseNumber": "79C1-654321"
}
```

**Response 200 OK (DriverProfileResponse)**  
Trả lại hồ sơ sau khi đã cập nhật `licenseNumber`.

---

### 3.4. Xem thông tin xe

- **Method**: `GET`  
- **URL**: `/api/drivers/me/vehicle`

**Headers**
- `Authorization: Bearer <accessToken>`

**Response 200 OK (DriverVehicleResponse)**

```json
{
  "vehicleId": "c48322ef-e71d-4078-8cc4-f499811f82ae",
  "brand": "Toyota",
  "model": "Vios",
  "plateNumber": "51A-12345",
  "color": "Black",
  "type": "CAR_4",
  "registrationDate": "2023-01-15"
}
```

Nếu chưa có xe → 404 + error từ common-lib (`NotFoundException`).

---

### 3.5. Cập nhật xe

- **Method**: `PUT`  
- **URL**: `/api/drivers/me/vehicle`

**Headers**
- `Authorization: Bearer <accessToken>`
- `Content-Type: application/json`

**Request body**

```json
{
  "brand": "Hyundai",
  "model": "Accent",
  "plateNumber": "51H-88888",
  "color": "White",
  "type": "CAR_4",
  "registrationDate": "2022-08-10"
}
```

**Response 200 OK (DriverVehicleResponse)**  
Trả lại thông tin xe mới.

---

### 3.6. Bật / tắt trạng thái sẵn sàng nhận chuyến

#### Go online

- **Method**: `PATCH`  
- **URL**: `/api/drivers/me/status/online`

**Headers**
- `Authorization: Bearer <accessToken>`

**Body**: none

**Response 200 OK (DriverStatusResponse)**

```json
{
  "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
  "accountStatus": "ACTIVE",
  "availabilityStatus": "ONLINE"
}
```

> Lưu ý: nếu tài khoản chưa được duyệt (`PENDING_VERIFICATION`, `REJECTED`, `BANNED`) sẽ trả lỗi `BusinessException` (400).

#### Go offline

- **Method**: `PATCH`  
- **URL**: `/api/drivers/me/status/offline`

**Headers**
- `Authorization: Bearer <accessToken>`

**Response 200 OK (DriverStatusResponse)**

```json
{
  "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
  "accountStatus": "ACTIVE",
  "availabilityStatus": "OFFLINE"
}
```

---

### 3.7. Xem rating của tài xế

- **Method**: `GET`  
- **URL**: `/api/drivers/{driverId}/rating`
- **Role**: Bất kỳ user đã đăng nhập (miễn có JWT hợp lệ)

**Response 200 OK (DriverRatingResponse)**

```json
{
  "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
  "rating": 4.8
}
```

---

### 3.8. Admin – list tài xế theo trạng thái

- **Method**: `GET`  
- **URL**: `/api/drivers?status=PENDING_VERIFICATION`  
- **Role**: ADMIN

**Response 200 OK (List\<DriverProfileResponse\>)**

```json
[
  {
    "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
    "userId": "a7f2e006-2cd1-45a9-abf1-55014f3aedce",
    "licenseNumber": "79C1-123456",
    "accountStatus": "PENDING_VERIFICATION",
    "availabilityStatus": "OFFLINE",
    "rating": 0.0,
    "completedTrips": 0,
    "vehicle": { ... },
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

`status` có thể là: `PENDING_VERIFICATION`, `ACTIVE`, `REJECTED`, `BANNED`. Nếu không truyền `status` → trả tất cả.

---

### 3.9. Admin – duyệt / từ chối / khóa / mở khóa

Header chung:
- `Authorization: Bearer <ADMIN_TOKEN>`

#### Approve driver

- **Method**: `PATCH`  
- **URL**: `/api/drivers/{driverId}/approve`

**Response 200 OK (DriverProfileResponse)** – `accountStatus` chuyển sang `ACTIVE`.

#### Reject driver

- **Method**: `PATCH`  
- **URL**: `/api/drivers/{driverId}/reject`

**Response 200 OK (DriverProfileResponse)** – `accountStatus = REJECTED`.

#### Suspend (khóa)

- **Method**: `PATCH`  
- **URL**: `/api/drivers/{driverId}/suspend`

**Response 200 OK (DriverProfileResponse)** – `accountStatus = BANNED`, `availabilityStatus = OFFLINE`.

#### Unsuspend (mở khóa)

- **Method**: `PATCH`  
- **URL**: `/api/drivers/{driverId}/unsuspend`

**Response 200 OK (DriverProfileResponse)** – `accountStatus` quay về `ACTIVE`.

---

---

## 4. Format lỗi chung (Error Responses)

Tất cả service (kể cả DriverService) dùng **GlobalExceptionHandler** từ `gomirai-common-lib`, nên lỗi sẽ có **format chuẩn**:

### 4.1. Lỗi validation (`400 Bad Request`)

Ví dụ khi body bị thiếu field bắt buộc:

```json
{
  "timestamp": "2025-11-25T12:00:00.000",
  "status": 400,
  "error": "Validation Error",
  "message": "Request validation failed",
  "errors": {
    "licenseNumber": "License number is required",
    "vehicleBrand": "Vehicle brand is required"
  }
}
```

Áp dụng cho các API có body: `/apply`, `/me` (PUT), `/me/vehicle`.

### 4.2. Lỗi business / not found

- **Không tìm thấy hồ sơ driver** (gọi `/me`, `/me/vehicle`, `/status/*`, `/approve`… với driverId không tồn tại):

```json
{
  "timestamp": "2025-11-25T12:00:00.000",
  "status": 404,
  "error": "Not Found",
  "message": "Không tìm thấy tài xế: 63994eca-1964-4a01-9363-331a24f62d9e"
}
```

- **Vi phạm nghiệp vụ** (ví dụ bật ONLINE khi account chưa ACTIVE, hoặc unsuspend tài xế không bị khóa):

```json
{
  "timestamp": "2025-11-25T12:00:00.000",
  "status": 400,
  "error": "Bad Request",
  "message": "Chỉ tài khoản đã duyệt mới được thay đổi trạng thái nhận cuốc."
}
```

### 4.3. Lỗi quyền hạn / JWT

- **401 Unauthorized** – thiếu hoặc JWT sai:

```json
{
  "timestamp": "2025-11-25T12:00:00.000",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```

- **403 Forbidden** – không đủ quyền (ví dụ user thường gọi API admin):

```json
{
  "timestamp": "2025-11-25T12:00:00.000",
  "status": 403,
  "error": "Forbidden",
  "message": "You are not authorized to access this resource"
}
```

---

## 5. Gợi ý config Postman

- Tạo **Collection** `DriverService`.
- Tạo **Environment**:
  - `baseUrl = http://localhost:8080`
  - `accessToken = <JWT user thường>`
  - `adminToken = <JWT admin>`
- Trong mỗi request:
  - URL: `{{baseUrl}}/api/drivers/...`
  - Header:  
    - Cho user: `Authorization: Bearer {{accessToken}}`  
    - Cho admin: `Authorization: Bearer {{adminToken}}`

Như vậy bạn có thể nhanh chóng chạy toàn bộ test trên Postman chỉ bằng cách đổi token trong Environment.
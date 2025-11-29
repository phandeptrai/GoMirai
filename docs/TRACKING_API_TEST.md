# Tracking Service - API Test Guide

## 🔍 Tìm Tài Xế Lân Cận

### Endpoint
```
POST /api/tracking/nearby
```

### Gateway URL
```
http://localhost:8080/api/tracking/nearby
```

### Authentication
- **Required**: ✅ Yes (bất kỳ user đã login)
- **Role**: `CUSTOMER`, `DRIVER`, hoặc `ADMIN` (bất kỳ role nào)
- **Header**: `Authorization: Bearer <accessToken>`

---

## 📋 Request Body

### Schema
```json
{
  "latitude": 10.762622,        // Required: Vĩ độ (độ)
  "longitude": 106.660172,       // Required: Kinh độ (độ)
  "radius": 2000,                // Required: Bán kính tìm kiếm (mét)
  "vehicleType": "MOTORBIKE",     // Optional: Loại xe (CAR_4, CAR_7, MOTORBIKE)
  "status": "ONLINE",            // Optional: Trạng thái (ONLINE, OFFLINE)
  "limit": 10                    // Optional: Số lượng tối đa (default: 10)
}
```

### Field Details

| Field | Type | Required | Description | Values |
|-------|------|----------|-------------|--------|
| `latitude` | double | ✅ Yes | Vĩ độ của vị trí tìm kiếm | -90.0 đến 90.0 |
| `longitude` | double | ✅ Yes | Kinh độ của vị trí tìm kiếm | -180.0 đến 180.0 |
| `radius` | double | ✅ Yes | Bán kính tìm kiếm (mét) | > 0 |
| `vehicleType` | string | ❌ Optional | Lọc theo loại xe | `CAR_4`, `CAR_7`, `MOTORBIKE` |
| `status` | string | ❌ Optional | Lọc theo trạng thái | `ONLINE`, `OFFLINE` |
| `limit` | int | ❌ Optional | Số lượng tối đa (default: 10) | > 0 |

---

## 📝 Test Cases

### Test Case 1: Tìm tất cả tài xế gần nhất (không filter)

**Request:**
```bash
curl -X POST http://localhost:8080/api/tracking/nearby \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "radius": 2000,
    "limit": 10
  }'
```

**Body (JSON):**
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 2000,
  "limit": 10
}
```

**Response 200 OK:**
```json
[
  {
    "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
    "latitude": 10.765000,
    "longitude": 106.662000,
    "status": "ONLINE",
    "vehicleType": "MOTORBIKE",
    "distance": 350.5,
    "lastUpdatedAt": 1701234567890
  },
  {
    "driverId": "a7f2e006-2cd1-45a9-abf1-55014f3aedce",
    "latitude": 10.770000,
    "longitude": 106.670000,
    "status": "ONLINE",
    "vehicleType": "CAR_4",
    "distance": 1200.8,
    "lastUpdatedAt": 1701234567891
  }
]
```

---

### Test Case 2: Tìm xe máy gần nhất

**Request:**
```bash
curl -X POST http://localhost:8080/api/tracking/nearby \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "radius": 2000,
    "vehicleType": "MOTORBIKE",
    "limit": 5
  }'
```

**Body (JSON):**
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 2000,
  "vehicleType": "MOTORBIKE",
  "limit": 5
}
```

**Response 200 OK:**
```json
[
  {
    "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
    "latitude": 10.765000,
    "longitude": 106.662000,
    "status": "ONLINE",
    "vehicleType": "MOTORBIKE",
    "distance": 350.5,
    "lastUpdatedAt": 1701234567890
  }
]
```

---

### Test Case 3: Tìm xe 4 chỗ đang online

**Request:**
```bash
curl -X POST http://localhost:8080/api/tracking/nearby \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "radius": 5000,
    "vehicleType": "CAR_4",
    "status": "ONLINE",
    "limit": 20
  }'
```

**Body (JSON):**
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 5000,
  "vehicleType": "CAR_4",
  "status": "ONLINE",
  "limit": 20
}
```

---

### Test Case 4: Tìm trong bán kính lớn (10km)

**Request:**
```bash
curl -X POST http://localhost:8080/api/tracking/nearby \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "radius": 10000,
    "limit": 50
  }'
```

**Body (JSON):**
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 10000,
  "limit": 50
}
```

---

## 🧪 Test với Postman

### 1. Setup Request
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/tracking/nearby`
- **Headers**:
  ```
  Authorization: Bearer YOUR_ACCESS_TOKEN
  Content-Type: application/json
  ```

### 2. Body (raw JSON)
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 2000,
  "vehicleType": "MOTORBIKE",
  "status": "ONLINE",
  "limit": 10
}
```

### 3. Expected Response
- **Status**: `200 OK`
- **Body**: Array of `DriverLocationResponse` objects
- **Sorted by**: Distance (gần → xa)

---

## 📍 Tọa Độ Mẫu (TP.HCM)

### Vị trí trung tâm TP.HCM:
```json
{
  "latitude": 10.762622,
  "longitude": 106.660172
}
```

### Các vị trí khác:
- **Quận 1**: `10.7769, 106.7009`
- **Quận 3**: `10.7831, 106.6962`
- **Quận 7**: `10.7314, 106.7225`
- **Quận Bình Thạnh**: `10.8024, 106.7148`

---

## ⚠️ Error Responses

### 401 Unauthorized
```json
{
  "timestamp": "2025-11-29T13:00:00",
  "status": 401,
  "error": "Unauthorized",
  "message": "Full authentication is required to access this resource"
}
```
**Nguyên nhân**: Chưa có token hoặc token hết hạn

---

### 400 Bad Request (Validation Error)
```json
{
  "timestamp": "2025-11-29T13:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "latitude",
      "message": "must not be null"
    },
    {
      "field": "radius",
      "message": "must be greater than 0"
    }
  ]
}
```
**Nguyên nhân**: Thiếu required fields hoặc giá trị không hợp lệ

---

### 400 Bad Request (Invalid Enum)
```json
{
  "timestamp": "2025-11-29T13:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid value 'INVALID_TYPE' for VehicleType. Allowed values are: [CAR_4, CAR_7, MOTORBIKE]"
}
```
**Nguyên nhân**: Giá trị enum không hợp lệ

---

### 400 Bad Request (No Drivers Found)
```json
{
  "timestamp": "2025-11-29T13:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Failed to search nearby drivers."
}
```
**Nguyên nhân**: Không tìm thấy tài xế trong bán kính (có thể do Redis connection issue)

---

## 🔄 Flow Test Hoàn Chỉnh

### Bước 1: Driver update location
```bash
# Driver app gọi để update vị trí
POST http://localhost:8080/api/tracking/location
Authorization: Bearer DRIVER_TOKEN
Content-Type: application/json

{
  "driverId": "63994eca-1964-4a01-9363-331a24f62d9e",
  "latitude": 10.765000,
  "longitude": 106.662000,
  "status": "ONLINE",
  "vehicleType": "MOTORBIKE",
  "lastUpdatedAt": 0
}
```

### Bước 2: Customer tìm nearby
```bash
# Customer app gọi để tìm tài xế gần nhất
POST http://localhost:8080/api/tracking/nearby
Authorization: Bearer CUSTOMER_TOKEN
Content-Type: application/json

{
  "latitude": 10.762622,
  "longitude": 106.660172,
  "radius": 2000,
  "vehicleType": "MOTORBIKE",
  "limit": 10
}
```

---

## 📊 Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `driverId` | string (UUID) | ID của tài xế |
| `latitude` | double | Vĩ độ hiện tại |
| `longitude` | double | Kinh độ hiện tại |
| `status` | string | Trạng thái (`ONLINE`, `OFFLINE`) |
| `vehicleType` | string | Loại xe (`CAR_4`, `CAR_7`, `MOTORBIKE`) |
| `distance` | double | Khoảng cách từ vị trí tìm kiếm (mét) |
| `lastUpdatedAt` | long | Timestamp cập nhật lần cuối (milliseconds) |

---

## 💡 Tips

1. **Bán kính hợp lý**: 
   - Trong thành phố: 2000-5000m (2-5km)
   - Ngoại thành: 5000-10000m (5-10km)

2. **Limit**: 
   - Không nên set quá lớn (> 50) → ảnh hưởng performance
   - Mặc định 10 là hợp lý

3. **Filter**: 
   - Nên filter theo `vehicleType` và `status` để giảm kết quả
   - Chỉ lấy driver `ONLINE` để đảm bảo sẵn sàng nhận cuốc

4. **Tọa độ**: 
   - Latitude: -90 đến 90 (TP.HCM: ~10.7)
   - Longitude: -180 đến 180 (TP.HCM: ~106.6)

---

## 🎯 Quick Test Commands

### PowerShell
```powershell
$token = "YOUR_ACCESS_TOKEN"
$body = @{
    latitude = 10.762622
    longitude = 106.660172
    radius = 2000
    vehicleType = "MOTORBIKE"
    limit = 10
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/tracking/nearby" `
    -Method POST `
    -Headers @{
        "Authorization" = "Bearer $token"
        "Content-Type" = "application/json"
    } `
    -Body $body
```

### Bash
```bash
TOKEN="YOUR_ACCESS_TOKEN"

curl -X POST http://localhost:8080/api/tracking/nearby \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "latitude": 10.762622,
    "longitude": 106.660172,
    "radius": 2000,
    "vehicleType": "MOTORBIKE",
    "limit": 10
  }'
```


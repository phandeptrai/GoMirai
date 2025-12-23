# Pricing Service API Documentation

Tài liệu này cung cấp thông tin về các API endpoints của Pricing Service để test trên Postman.

## Base URL
```
http://localhost:<port>/api/pricing
```

---

## 1. Estimate Pricing (Public API)

**Endpoint:** `POST /api/pricing/estimate`

**Description:** Ước tính giá cước dựa trên loại xe, khoảng cách, thời gian và khu vực.

**Authentication:** Không yêu cầu (hoặc có thể yêu cầu JWT tùy cấu hình)

### Request Body

```json
{
  "vehicleType": "MOTORBIKE",
  "distanceKm": 5.5,
  "durationMinute": 20,
  "region": "HCM"
}
```

### Request Validation

| Field | Type | Required | Validation Rules | Error Message |
|-------|------|----------|------------------|---------------|
| `vehicleType` | String | ✅ | NotBlank | "vehicleType is required" |
| `distanceKm` | Double | ✅ | Positive (> 0) | "distanceKm must be > 0" |
| `durationMinute` | Integer | ✅ | Positive (> 0) | "durationMinute must be > 0" |
| `region` | String | ✅ | NotBlank | "region is required" |

### Success Response (200 OK)

```json
{
  "estimatedFare": 50000,
  "appliedRuleId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Error Responses

#### 400 Bad Request - Validation Error
Khi request body không hợp lệ (Spring Validation):
```json
{
  "timestamp": "2024-01-01T00:00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": {
    "vehicleType": "vehicleType is required",
    "distanceKm": "distanceKm must be > 0"
  }
}
```

#### 400 Bad Request - Business Error
Khi dữ liệu không hợp lệ (từ service validation):
- Response Body: `"INVALID_VEHICLE_TYPE"`
- Response Body: `"INVALID_DISTANCE"`
- Response Body: `"INVALID_DURATION"`
- Response Body: `"INVALID_REGION"`

#### 404 Not Found
Khi không tìm thấy pricing rule:
- Response Body: `"PRICING_RULE_NOT_FOUND"`

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8082/api/pricing/estimate`  
**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "vehicleType": "MOTORBIKE",
  "distanceKm": 5.5,
  "durationMinute": 20,
  "region": "HCM"
}
```

---

## 2. Calculate Final Pricing (Internal API)

**Endpoint:** `POST /api/pricing/calculate-final`

**Description:** Tính toán giá cước cuối cùng dựa trên dữ liệu thực tế của chuyến đi. API này được gọi bởi Booking Service.

**Authentication:** ✅ Required - `ROLE_BOOKING_SERVICE`

### Request Parameters (Query)

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `estimatedDistanceKm` | Double | ✅ | Khoảng cách ước tính (km) |
| `estimatedDurationMinute` | Integer | ✅ | Thời gian ước tính (phút) |

### Request Body

```json
{
  "rideId": "ride-12345",
  "vehicleType": "MOTORBIKE",
  "actualDistanceKm": 5.8,
  "actualDurationMinute": 22,
  "region": "HCM"
}
```

### Request Validation

| Field | Type | Required | Validation Rules | Error Message |
|-------|------|----------|------------------|---------------|
| `rideId` | String | ✅ | NotBlank | "rideId is required" |
| `vehicleType` | String | ✅ | NotBlank | "vehicleType is required" |
| `actualDistanceKm` | Double | ✅ | Positive (> 0) | "actualDistanceKm must be > 0" |
| `actualDurationMinute` | Integer | ✅ | Positive (> 0) | "actualDurationMinute must be > 0" |
| `region` | String | ✅ | NotBlank | "region is required" |

### Business Rules

1. **Cheat Detection - Distance:**
   - Nếu `actualDistanceKm < estimatedDistanceKm * 0.5` → Error 409: `"POSSIBLE_CHEAT_DISTANCE"`

2. **Cheat Detection - Duration:**
   - Nếu `actualDurationMinute < estimatedDurationMinute * 0.5` → Error 409: `"POSSIBLE_CHEAT_DURATION"`

### Success Response (200 OK)

```json
{
  "estimatedFare": 52000,
  "appliedRuleId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Error Responses

#### 400 Bad Request - Validation Error
- Response Body: `"INVALID_RIDE_ID"`
- Response Body: `"INVALID_VEHICLE_TYPE"`
- Response Body: `"INVALID_DISTANCE"`
- Response Body: `"INVALID_DURATION"`
- Response Body: `"INVALID_REGION"`

#### 403 Forbidden
Khi không có quyền `ROLE_BOOKING_SERVICE`:
- Response Body: `"Access Denied: Insufficient Authority"`

#### 404 Not Found
- Response Body: `"PRICING_RULE_NOT_FOUND"`

#### 409 Conflict - Cheat Detection
- Response Body: `"POSSIBLE_CHEAT_DISTANCE"`
- Response Body: `"POSSIBLE_CHEAT_DURATION"`

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8082/api/pricing/calculate-final?estimatedDistanceKm=5.5&estimatedDurationMinute=20`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_BOOKING_SERVICE>
```

**Body (raw JSON):**
```json
{
  "rideId": "ride-12345",
  "vehicleType": "MOTORBIKE",
  "actualDistanceKm": 5.8,
  "actualDurationMinute": 22,
  "region": "HCM"
}
```

---

## 3. Create Pricing Rule (Admin API)

**Endpoint:** `POST /api/pricing/rules`

**Description:** Tạo mới một pricing rule.

**Authentication:** ✅ Required - `ROLE_ADMIN`

### Request Body

```json
{
  "ruleId": null,
  "vehicleType": "MOTORBIKE",
  "baseFare": 10000,
  "perKmRate": 5000,
  "perMinuteRate": 1000,
  "surgeMultiplier": 1.0,
  "region": "HCM",
  "active": true
}
```

**Note:** `ruleId` sẽ được tự động tạo nếu là `null`.

### Request Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ruleId` | UUID | ❌ | ID của rule (null nếu tạo mới) |
| `vehicleType` | String | ✅ | Loại xe (MOTORBIKE, CAR_4_SEAT, CAR_7_SEAT...) |
| `baseFare` | Double | ✅ | Giá cơ bản |
| `perKmRate` | Double | ✅ | Giá mỗi km |
| `perMinuteRate` | Double | ✅ | Giá mỗi phút |
| `surgeMultiplier` | Double | ❌ | Hệ số tăng giá (mặc định: 1.0) |
| `region` | String | ✅ | Khu vực (HCM, HN...) |
| `active` | Boolean | ❌ | Trạng thái hoạt động (mặc định: true) |

### Success Response (200 OK)

```json
{
  "ruleId": "550e8400-e29b-41d4-a716-446655440000",
  "vehicleType": "MOTORBIKE",
  "baseFare": 10000,
  "perKmRate": 5000,
  "perMinuteRate": 1000,
  "surgeMultiplier": 1.0,
  "region": "HCM",
  "active": true
}
```

### Error Responses

#### 403 Forbidden
Khi không có quyền `ROLE_ADMIN`:
- Response Body: `"Access Denied: Insufficient Authority"`

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8082/api/pricing/rules`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_ADMIN>
```

**Body (raw JSON):**
```json
{
  "vehicleType": "MOTORBIKE",
  "baseFare": 10000,
  "perKmRate": 5000,
  "perMinuteRate": 1000,
  "surgeMultiplier": 1.0,
  "region": "HCM",
  "active": true
}
```

---

## 4. Update Pricing Rule (Admin API)

**Endpoint:** `PUT /api/pricing/rules/{id}`

**Description:** Cập nhật một pricing rule theo ID.

**Authentication:** ✅ Required - `ROLE_ADMIN`

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | ✅ | ID của pricing rule cần cập nhật |

### Request Body

```json
{
  "vehicleType": "MOTORBIKE",
  "baseFare": 12000,
  "perKmRate": 5500,
  "perMinuteRate": 1200,
  "surgeMultiplier": 1.2,
  "region": "HCM",
  "active": true
}
```

**Note:** `ruleId` trong body sẽ bị ghi đè bởi `id` trong path.

### Success Response (200 OK)

```json
{
  "ruleId": "550e8400-e29b-41d4-a716-446655440000",
  "vehicleType": "MOTORBIKE",
  "baseFare": 12000,
  "perKmRate": 5500,
  "perMinuteRate": 1200,
  "surgeMultiplier": 1.2,
  "region": "HCM",
  "active": true
}
```

### Error Responses

#### 403 Forbidden
- Response Body: `"Access Denied: Insufficient Authority"`

### Example Postman Request

**Method:** PUT  
**URL:** `http://localhost:8082/api/pricing/rules/550e8400-e29b-41d4-a716-446655440000`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_ADMIN>
```

**Body (raw JSON):**
```json
{
  "vehicleType": "MOTORBIKE",
  "baseFare": 12000,
  "perKmRate": 5500,
  "perMinuteRate": 1200,
  "surgeMultiplier": 1.2,
  "region": "HCM",
  "active": true
}
```

---

## 5. List All Pricing Rules (Admin API)

**Endpoint:** `GET /api/pricing/rules`

**Description:** Lấy danh sách tất cả pricing rules.

**Authentication:** ✅ Required - `ROLE_ADMIN`

### Success Response (200 OK)

```json
[
  {
    "ruleId": "550e8400-e29b-41d4-a716-446655440000",
    "vehicleType": "MOTORBIKE",
    "baseFare": 10000,
    "perKmRate": 5000,
    "perMinuteRate": 1000,
    "surgeMultiplier": 1.0,
    "region": "HCM",
    "active": true
  },
  {
    "ruleId": "660e8400-e29b-41d4-a716-446655440001",
    "vehicleType": "CAR_4_SEAT",
    "baseFare": 20000,
    "perKmRate": 8000,
    "perMinuteRate": 2000,
    "surgeMultiplier": 1.0,
    "region": "HCM",
    "active": true
  }
]
```

### Error Responses

#### 403 Forbidden
- Response Body: `"Access Denied: Insufficient Authority"`

### Example Postman Request

**Method:** GET  
**URL:** `http://localhost:8082/api/pricing/rules`  
**Headers:**
```
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_ADMIN>
```

---

## 6. Debug Auth (Debug API)

**Endpoint:** `GET /api/pricing/debug/auth`

**Description:** Debug endpoint để kiểm tra thông tin authentication (nên xóa sau khi test).

**Authentication:** Không yêu cầu

### Success Response (200 OK)

```json
{
  "authenticated": true,
  "principal": "user@example.com",
  "authorities": ["ROLE_USER"]
}
```

hoặc

```json
{
  "authenticated": false,
  "message": "No authentication found"
}
```

---

## Error Codes Summary

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `INVALID_VEHICLE_TYPE` | 400 | Loại xe không hợp lệ |
| `INVALID_DISTANCE` | 400 | Khoảng cách không hợp lệ (<= 0) |
| `INVALID_DURATION` | 400 | Thời gian không hợp lệ (<= 0) |
| `INVALID_REGION` | 400 | Khu vực không hợp lệ |
| `INVALID_RIDE_ID` | 400 | Ride ID không hợp lệ |
| `PRICING_RULE_NOT_FOUND` | 404 | Không tìm thấy pricing rule |
| `POSSIBLE_CHEAT_DISTANCE` | 409 | Khoảng cách thực tế quá nhỏ so với ước tính |
| `POSSIBLE_CHEAT_DURATION` | 409 | Thời gian thực tế quá nhỏ so với ước tính |
| `Access Denied: Insufficient Authority` | 403 | Không có quyền truy cập |

---

## Test Cases cho Postman

### Test Case 1: Estimate Pricing - Success
- **Request:** Valid request với đầy đủ thông tin
- **Expected:** 200 OK với `estimatedFare` và `appliedRuleId`

### Test Case 2: Estimate Pricing - Missing vehicleType
- **Request:** Thiếu `vehicleType`
- **Expected:** 400 Bad Request với validation error

### Test Case 3: Estimate Pricing - Invalid distance
- **Request:** `distanceKm = -5`
- **Expected:** 400 Bad Request với "distanceKm must be > 0"

### Test Case 4: Estimate Pricing - No Pricing Rule
- **Request:** Valid request nhưng không có pricing rule cho vehicleType/region
- **Expected:** 404 Not Found với "PRICING_RULE_NOT_FOUND"

### Test Case 5: Calculate Final - Cheat Detection Distance
- **Request:** `actualDistanceKm = 2.0`, `estimatedDistanceKm = 10.0`
- **Expected:** 409 Conflict với "POSSIBLE_CHEAT_DISTANCE"

### Test Case 6: Calculate Final - Unauthorized
- **Request:** Không có JWT token hoặc token không có `ROLE_BOOKING_SERVICE`
- **Expected:** 403 Forbidden với "Access Denied: Insufficient Authority"

### Test Case 7: Create Pricing Rule - Success
- **Request:** Valid pricing rule với JWT có `ROLE_ADMIN`
- **Expected:** 200 OK với pricing rule đã tạo (có `ruleId`)

### Test Case 8: Create Pricing Rule - Unauthorized
- **Request:** Valid pricing rule nhưng không có quyền ADMIN
- **Expected:** 403 Forbidden

---

## Notes

1. **Authentication:** 
   - Các API public không yêu cầu authentication (hoặc tùy cấu hình SecurityConfig)
   - Các API admin yêu cầu `ROLE_ADMIN`
   - API calculate-final yêu cầu `ROLE_BOOKING_SERVICE`

2. **Vehicle Types:** Các giá trị có thể: `MOTORBIKE`, `CAR_4_SEAT`, `CAR_7_SEAT`, etc.

3. **Regions:** Các giá trị có thể: `HCM`, `HN`, etc.

4. **Fare Calculation:**
   ```
   fare = (baseFare + distanceKm * perKmRate + durationMinute * perMinuteRate) * surgeMultiplier
   ```

5. **Cheat Detection:** 
   - Actual distance/duration phải >= 50% của estimated
   - Nếu không, sẽ trả về 409 Conflict








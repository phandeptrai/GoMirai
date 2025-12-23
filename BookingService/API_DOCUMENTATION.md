# Booking Service API Documentation

Tài liệu này cung cấp thông tin về các API endpoints của Booking Service để test trên Postman.

## Base URL
```
http://localhost:<port>/api/booking
```

---

## Response Format

Tất cả API responses đều sử dụng format `ApiResponse<T>`:

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Success message",
  "data": { ... }
}
```

---

## 1. Create Booking

**Endpoint:** `POST /api/booking`

**Description:** Tạo một booking mới. Service sẽ tự động gọi Map Service để tính route và Pricing Service để tính giá.

**Authentication:** ✅ Required - `ROLE_CUSTOMER`

### Request Body

```json
{
  "pickupLocation": {
    "fullAddress": "123 Nguyễn Văn A, Quận 1, TP.HCM",
    "latitude": 10.7769,
    "longitude": 106.7009
  },
  "dropoffLocation": {
    "fullAddress": "456 Lê Lợi, Quận 1, TP.HCM",
    "latitude": 10.7720,
    "longitude": 106.6980
  },
  "vehicleType": "MOTORBIKE",
  "paymentMethod": "CASH",
  "scheduledAt": null,
  "notes": "Xin chào",
  "idempotencyKey": "unique-key-123"
}
```

### Request Validation

| Field | Type | Required | Validation Rules | Error Message |
|-------|------|----------|------------------|---------------|
| `pickupLocation` | AddressSnapshot | ✅ | NotNull, Valid | "Pickup location is required" |
| `pickupLocation.fullAddress` | String | ❌ | - | - |
| `pickupLocation.latitude` | Double | ✅ | NotNull, Min(-90), Max(90) | "Latitude is required" / "Latitude must be between -90 and 90" |
| `pickupLocation.longitude` | Double | ✅ | NotNull, Min(-180), Max(180) | "Longitude is required" / "Longitude must be between -180 and 180" |
| `dropoffLocation` | AddressSnapshot | ✅ | NotNull, Valid | "Dropoff location is required" |
| `dropoffLocation.fullAddress` | String | ❌ | - | - |
| `dropoffLocation.latitude` | Double | ✅ | NotNull, Min(-90), Max(90) | "Latitude is required" / "Latitude must be between -90 and 90" |
| `dropoffLocation.longitude` | Double | ✅ | NotNull, Min(-180), Max(180) | "Longitude is required" / "Longitude must be between -180 and 180" |
| `vehicleType` | VehicleType | ✅ | NotNull | "Vehicle type is required" |
| `paymentMethod` | PaymentMethod | ✅ | NotNull | "Payment method is required" |
| `scheduledAt` | LocalDateTime | ❌ | - | Nullable (null = immediate booking) |
| `notes` | String | ❌ | Max(500) | "Notes must not exceed 500 characters" |
| `idempotencyKey` | String | ❌ | - | Optional, for idempotent creation |

### VehicleType Enum Values
- `MOTORBIKE`
- `CAR_4`
- `CAR_7`

### PaymentMethod Enum Values
- `CASH` - Tiền mặt
- `WALLET` - Ví điện tử

### Success Response (201 Created)

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Booking created successfully",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "customerId": "660e8400-e29b-41d4-a716-446655440001",
    "driverId": null,
    "status": "PENDING",
    "pickupLocation": {
      "fullAddress": "123 Nguyễn Văn A, Quận 1, TP.HCM",
      "latitude": 10.7769,
      "longitude": 106.7009
    },
    "dropoffLocation": {
      "fullAddress": "456 Lê Lợi, Quận 1, TP.HCM",
      "latitude": 10.7720,
      "longitude": 106.6980
    },
    "vehicleType": "MOTORBIKE",
    "paymentMethod": "CASH",
    "price": {
      "baseFare": 20000.0,
      "distanceFare": 25000.0,
      "timeFare": 5000.0,
      "surgeMultiplier": 1.0,
      "discount": 0.0,
      "finalAmount": 50000.0,
      "currency": "VND",
      "estimatedDistanceKm": 5.5,
      "estimatedDurationMinutes": 20,
      "pricingRuleId": "770e8400-e29b-41d4-a716-446655440002"
    },
    "estimatedDistanceKm": 5.5,
    "actualDistanceKm": null,
    "estimatedDurationMinutes": 20,
    "actualDurationMinutes": null,
    "routePolyline": "encoded_polyline_string",
    "scheduledAt": null,
    "actualPickupTime": null,
    "actualDropoffTime": null,
    "paymentId": null,
    "cancelReason": null,
    "notes": "Xin chào",
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T12:00:00"
  }
}
```

### Error Responses

#### 400 Bad Request - Validation Error
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Request validation failed. Please check the errors for each field.",
  "data": {
    "pickupLocation.latitude": "Latitude is required",
    "vehicleType": "Vehicle type is required"
  }
}
```

#### 400 Bad Request - Business Error
Khi Map Service hoặc Pricing Service không available:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "MAP_UNAVAILABLE: Unable to calculate route. Please try again later.",
  "data": null
}
```

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "PRICING_RULE_NOT_FOUND: Không tìm thấy quy tắc giá cho loại xe MOTORBIKE tại khu vực HCM. Vui lòng liên hệ quản trị viên để tạo quy tắc giá hoặc thử lại sau.",
  "data": null
}
```

#### 401 Unauthorized
Khi không có JWT token hoặc token không hợp lệ:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Unauthorized",
  "data": null
}
```

#### 403 Forbidden
Khi không có quyền `ROLE_CUSTOMER`:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Access Denied: Insufficient Authority",
  "data": null
}
```

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8083/api/booking`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_CUSTOMER>
```

**Body (raw JSON):**
```json
{
  "pickupLocation": {
    "fullAddress": "123 Nguyễn Văn A, Quận 1, TP.HCM",
    "latitude": 10.7769,
    "longitude": 106.7009
  },
  "dropoffLocation": {
    "fullAddress": "456 Lê Lợi, Quận 1, TP.HCM",
    "latitude": 10.7720,
    "longitude": 106.6980
  },
  "vehicleType": "MOTORBIKE",
  "paymentMethod": "CASH",
  "notes": "Xin chào"
}
```

---

## 2. Get Booking by ID

**Endpoint:** `GET /api/booking/{bookingId}`

**Description:** Lấy thông tin chi tiết của một booking theo ID.

**Authentication:** ✅ Required - `isAuthenticated()` (Customer hoặc Driver của booking đó)

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `bookingId` | UUID | ✅ | ID của booking |

### Success Response (200 OK)

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Success",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "customerId": "660e8400-e29b-41d4-a716-446655440001",
    "driverId": "770e8400-e29b-41d4-a716-446655440003",
    "status": "MATCHED",
    "pickupLocation": { ... },
    "dropoffLocation": { ... },
    "vehicleType": "MOTORBIKE",
    "paymentMethod": "CASH",
    "price": { ... },
    "estimatedDistanceKm": 5.5,
    "actualDistanceKm": null,
    "estimatedDurationMinutes": 20,
    "actualDurationMinutes": null,
    "routePolyline": "encoded_polyline_string",
    "scheduledAt": null,
    "actualPickupTime": null,
    "actualDropoffTime": null,
    "paymentId": null,
    "cancelReason": null,
    "notes": "Xin chào",
    "createdAt": "2024-01-01T12:00:00",
    "updatedAt": "2024-01-01T12:00:00"
  }
}
```

### Error Responses

#### 401 Unauthorized
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Unauthorized",
  "data": null
}
```

#### 403 Forbidden
Khi user không phải là customer hoặc driver của booking:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "FORBIDDEN: You don't have permission to view this booking",
  "data": null
}
```

#### 404 Not Found
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Booking not found: 550e8400-e29b-41d4-a716-446655440000",
  "data": null
}
```

### Example Postman Request

**Method:** GET  
**URL:** `http://localhost:8083/api/booking/550e8400-e29b-41d4-a716-446655440000`  
**Headers:**
```
Authorization: Bearer <JWT_TOKEN>
```

---

## 3. Get Customer Bookings

**Endpoint:** `GET /api/booking/me`

**Description:** Lấy danh sách bookings của customer hiện tại (có phân trang).

**Authentication:** ✅ Required - `ROLE_CUSTOMER`

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `status` | BookingStatus | ❌ | null | Lọc theo status (PENDING, MATCHED, COMPLETED, CANCELED, etc.) |
| `page` | Integer | ❌ | 0 | Số trang (0-based) |
| `size` | Integer | ❌ | 10 | Số items mỗi trang |

### BookingStatus Enum Values
- `PENDING` - Đã tạo, chờ tài xế nhận
- `MATCHED` - Đã có tài xế nhận
- `DRIVER_ARRIVED` - Tài xế đã đến điểm đón
- `IN_PROGRESS` - Đang di chuyển
- `COMPLETED` - Hoàn thành
- `CANCELED` - Đã hủy
- `EXPIRED` - Hết hạn (không có tài xế nhận)
- `NO_DRIVER_FOUND` - Không tìm thấy tài xế

### Success Response (200 OK)

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Success",
  "data": {
    "content": [
      {
        "bookingId": "550e8400-e29b-41d4-a716-446655440000",
        "customerId": "660e8400-e29b-41d4-a716-446655440001",
        "driverId": "770e8400-e29b-41d4-a716-446655440003",
        "status": "MATCHED",
        ...
      }
    ],
    "pageable": {
      "pageNumber": 0,
      "pageSize": 10,
      "sort": {
        "sorted": false
      }
    },
    "totalElements": 25,
    "totalPages": 3,
    "last": false,
    "first": true,
    "numberOfElements": 10,
    "size": 10,
    "number": 0,
    "empty": false
  }
}
```

### Error Responses

#### 401 Unauthorized
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Unauthorized",
  "data": null
}
```

#### 403 Forbidden
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Access Denied: Insufficient Authority",
  "data": null
}
```

### Example Postman Request

**Method:** GET  
**URL:** `http://localhost:8083/api/booking/me?status=PENDING&page=0&size=10`  
**Headers:**
```
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_CUSTOMER>
```

---

## 4. Get Driver Bookings

**Endpoint:** `GET /api/booking/driver/me`

**Description:** Lấy danh sách bookings của driver hiện tại (có phân trang).

**Authentication:** ✅ Required - `ROLE_DRIVER`

### Query Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `status` | BookingStatus | ❌ | null | Lọc theo status |
| `page` | Integer | ❌ | 0 | Số trang (0-based) |
| `size` | Integer | ❌ | 10 | Số items mỗi trang |

### Success Response (200 OK)

Tương tự như Get Customer Bookings, nhưng trả về bookings của driver.

### Error Responses

Tương tự như Get Customer Bookings.

### Example Postman Request

**Method:** GET  
**URL:** `http://localhost:8083/api/booking/driver/me?status=MATCHED&page=0&size=10`  
**Headers:**
```
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_DRIVER>
```

---

## 5. Cancel Booking

**Endpoint:** `POST /api/booking/{bookingId}/cancel`

**Description:** Hủy một booking. Chỉ customer có thể hủy booking của mình.

**Authentication:** ✅ Required - `ROLE_CUSTOMER`

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `bookingId` | UUID | ✅ | ID của booking cần hủy |

### Request Body

```json
{
  "reason": "Thay đổi kế hoạch"
}
```

### Request Validation

| Field | Type | Required | Validation Rules | Error Message |
|-------|------|----------|------------------|---------------|
| `reason` | String | ✅ | NotBlank | "Cancel reason is required" |

### Business Rules

- Chỉ có thể hủy booking khi status là `PENDING` hoặc `MATCHED`
- Chỉ customer (owner) mới có thể hủy booking

### Success Response (200 OK)

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Booking canceled successfully",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "CANCELED",
    "cancelReason": "Thay đổi kế hoạch",
    ...
  }
}
```

### Error Responses

#### 400 Bad Request - Validation Error
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Request validation failed. Please check the errors for each field.",
  "data": {
    "reason": "Cancel reason is required"
  }
}
```

#### 400 Bad Request - Business Error
Khi booking không thể hủy:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "INVALID_STATUS: Booking cannot be canceled. Current status: COMPLETED",
  "data": null
}
```

#### 403 Forbidden
Khi user không phải là customer của booking:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "FORBIDDEN: Only customer can cancel booking",
  "data": null
}
```

#### 404 Not Found
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Booking not found: 550e8400-e29b-41d4-a716-446655440000",
  "data": null
}
```

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8083/api/booking/550e8400-e29b-41d4-a716-446655440000/cancel`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_CUSTOMER>
```

**Body (raw JSON):**
```json
{
  "reason": "Thay đổi kế hoạch"
}
```

---

## 6. Complete Booking

**Endpoint:** `POST /api/booking/{bookingId}/complete`

**Description:** Hoàn thành một booking (khi chuyến đi kết thúc). Customer hoặc Driver đều có thể complete booking.

**Authentication:** ✅ Required - `hasAnyRole('CUSTOMER', 'DRIVER')`

### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `bookingId` | UUID | ✅ | ID của booking cần complete |

### Request Body

```json
{
  "actualDistanceKm": 5.8,
  "actualDurationMinutes": 22
}
```

### Request Validation

| Field | Type | Required | Validation Rules | Error Message |
|-------|------|----------|------------------|---------------|
| `actualDistanceKm` | Double | ✅ | Positive (> 0) | "Actual distance must be positive" |
| `actualDurationMinutes` | Integer | ✅ | Positive (> 0) | "Actual duration must be positive" |

### Business Rules

- Booking phải có status là `IN_PROGRESS` mới có thể complete
- User phải là customer hoặc driver của booking đó
- Sau khi complete, service sẽ gọi Pricing Service để tính giá cuối cùng dựa trên actual distance/duration

### Success Response (200 OK)

```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": true,
  "message": "Booking completed successfully",
  "data": {
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "COMPLETED",
    "actualDistanceKm": 5.8,
    "actualDurationMinutes": 22,
    "actualDropoffTime": "2024-01-01T12:30:00",
    "price": {
      "finalAmount": 52000.0,
      ...
    },
    ...
  }
}
```

### Error Responses

#### 400 Bad Request - Validation Error
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Request validation failed. Please check the errors for each field.",
  "data": {
    "actualDistanceKm": "Actual distance must be positive"
  }
}
```

#### 400 Bad Request - Business Error
Khi booking không ở trạng thái IN_PROGRESS:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "INVALID_STATUS: Booking must be IN_PROGRESS to complete. Current status: PENDING",
  "data": null
}
```

#### 400 Bad Request - Pricing Service Error
Khi Pricing Service trả về lỗi (ví dụ: cheat detection):
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "POSSIBLE_CHEAT_DISTANCE",
  "data": null
}
```

#### 403 Forbidden
Khi user không phải là customer hoặc driver:
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "FORBIDDEN: You don't have permission to complete this booking",
  "data": null
}
```

#### 404 Not Found
```json
{
  "timestamp": "2024-01-01T12:00:00.000",
  "success": false,
  "message": "Booking not found: 550e8400-e29b-41d4-a716-446655440000",
  "data": null
}
```

### Example Postman Request

**Method:** POST  
**URL:** `http://localhost:8083/api/booking/550e8400-e29b-41d4-a716-446655440000/complete`  
**Headers:**
```
Content-Type: application/json
Authorization: Bearer <JWT_TOKEN_WITH_ROLE_CUSTOMER_OR_DRIVER>
```

**Body (raw JSON):**
```json
{
  "actualDistanceKm": 5.8,
  "actualDurationMinutes": 22
}
```

---

## Error Codes Summary

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `MAP_UNAVAILABLE` | 400 | Map Service không available |
| `PRICING_UNAVAILABLE` | 400 | Pricing Service không available |
| `PRICING_RULE_NOT_FOUND` | 400 | Không tìm thấy pricing rule |
| `POSSIBLE_CHEAT_DISTANCE` | 400 | Khoảng cách thực tế quá nhỏ so với ước tính |
| `POSSIBLE_CHEAT_DURATION` | 400 | Thời gian thực tế quá nhỏ so với ước tính |
| `INVALID_STATUS` | 400 | Trạng thái booking không hợp lệ cho thao tác này |
| `FORBIDDEN` | 403 | Không có quyền thực hiện thao tác |
| `Unauthorized` | 401 | Chưa đăng nhập hoặc token không hợp lệ |
| `Booking not found` | 404 | Không tìm thấy booking |

---

## Test Cases cho Postman

### Test Case 1: Create Booking - Success
- **Request:** Valid request với đầy đủ thông tin
- **Expected:** 201 Created với booking đã tạo (status = PENDING)

### Test Case 2: Create Booking - Missing pickupLocation
- **Request:** Thiếu `pickupLocation`
- **Expected:** 400 Bad Request với validation error

### Test Case 3: Create Booking - Invalid latitude
- **Request:** `latitude = 100` (vượt quá 90)
- **Expected:** 400 Bad Request với "Latitude must be between -90 and 90"

### Test Case 4: Create Booking - Map Service Unavailable
- **Request:** Valid request nhưng Map Service down
- **Expected:** 400 Bad Request với "MAP_UNAVAILABLE"

### Test Case 5: Create Booking - Pricing Service Unavailable
- **Request:** Valid request nhưng Pricing Service down
- **Expected:** 400 Bad Request với "PRICING_UNAVAILABLE"

### Test Case 6: Get Booking - Unauthorized
- **Request:** Không có JWT token
- **Expected:** 401 Unauthorized

### Test Case 7: Get Booking - Forbidden (not owner)
- **Request:** User không phải là customer/driver của booking
- **Expected:** 403 Forbidden

### Test Case 8: Cancel Booking - Invalid Status
- **Request:** Cancel booking có status = COMPLETED
- **Expected:** 400 Bad Request với "INVALID_STATUS"

### Test Case 9: Complete Booking - Invalid Status
- **Request:** Complete booking có status = PENDING
- **Expected:** 400 Bad Request với "INVALID_STATUS"

### Test Case 10: Complete Booking - Cheat Detection
- **Request:** `actualDistanceKm` quá nhỏ so với `estimatedDistanceKm`
- **Expected:** 400 Bad Request với "POSSIBLE_CHEAT_DISTANCE"

---

## Notes

1. **Authentication:** 
   - Tất cả API đều yêu cầu JWT token
   - Mỗi API có yêu cầu role cụ thể (CUSTOMER, DRIVER, hoặc isAuthenticated)

2. **Vehicle Types:** 
   - `MOTORBIKE`
   - `CAR_4`
   - `CAR_7`

3. **Payment Methods:**
   - `CASH` - Tiền mặt
   - `WALLET` - Ví điện tử

4. **Booking Status Flow:**
   ```
   PENDING → MATCHED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
                      ↓
                   CANCELED / EXPIRED / NO_DRIVER_FOUND
   ```

5. **Dependencies:**
   - Booking Service phụ thuộc vào Map Service (để tính route)
   - Booking Service phụ thuộc vào Pricing Service (để tính giá)
   - Nếu các service này không available, booking sẽ fail

6. **Idempotency:**
   - Có thể sử dụng `idempotencyKey` để đảm bảo không tạo duplicate booking

7. **Pagination:**
   - Get Customer/Driver Bookings hỗ trợ phân trang với `page` và `size`
   - Có thể filter theo `status`








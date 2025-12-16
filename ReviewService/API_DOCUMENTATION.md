# Review Service - API Documentation

## Tổng Quan

Review Service cung cấp các API để quản lý đánh giá và xếp hạng của các tài xế hoặc hành khách. Service này được tích hợp với JWT Authentication và yêu cầu token cho các endpoint bảo mật.

---

## Base URL

```
http://localhost:8089/api/review
```

Hoặc thông qua API Gateway:
```
http://localhost:8080/api/review
```

---

## Authentication

### Header yêu cầu (cho các endpoint được bảo mật)

```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Loại User

- `ROLE_CUSTOMER`: Khách hàng/người dùng
- `ROLE_DRIVER`: Tài xế

---

## API Endpoints

### 1. Tạo Review (Create Review)

**Endpoint**: `POST /api/review`

**Yêu cầu xác thực**: ✅ Có (ROLE_CUSTOMER hoặc ROLE_DRIVER)

**Mô tả**: Tạo một đánh giá mới cho một người khác dựa trên một chuyến đi.

#### Request

**Headers**:
```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

**Body**:
```json
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 4.5,
  "comment": "Tài xế rất chuyên nghiệp và xe sạch sẽ"
}
```

**Request Fields**:

| Field | Type | Required | Constraints | Mô tả |
|-------|------|----------|-------------|-------|
| `bookingId` | UUID | ✅ Yes | Valid UUID | ID của chuyến đi mà đánh giá liên quan |
| `revieweeId` | UUID | ✅ Yes | Valid UUID | ID của người được đánh giá |
| `rating` | Double | ✅ Yes | 1.0 - 5.0 | Điểm xếp hạng (1-5 sao) |
| `comment` | String | ❌ No | Max 500 chars | Bình luận/nhận xét |

#### Response

**Status Code**: `201 Created`

**Success Response (200)**:
```json
{
  "reviewId": "770e8400-e29b-41d4-a716-446655440002",
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "reviewerId": "880e8400-e29b-41d4-a716-446655440003",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 4.5,
  "comment": "Tài xế rất chuyên nghiệp và xe sạch sẽ",
  "createdAt": "2025-12-17T10:30:45"
}
```

**Error Responses**:

| Status | Error Code | Mô tả |
|--------|-----------|-------|
| `400` | `INVALID_RATING` | Rating không nằm trong khoảng 1.0 - 5.0 |
| `400` | `CANNOT_REVIEW_SELF` | Người dùng không thể đánh giá chính mình |
| `400` | `REVIEW_ALREADY_EXISTS` | Đã tồn tại đánh giá cho cặp (reviewer, reviewee, booking) này |
| `401` | `UNAUTHORIZED` | Token không hợp lệ hoặc hết hạn |
| `403` | `FORBIDDEN` | User không có quyền (không phải ROLE_CUSTOMER hoặc ROLE_DRIVER) |

**Example Error Response**:
```json
{
  "error": "REVIEW_ALREADY_EXISTS",
  "message": "A review already exists for this booking and reviewee",
  "status": 400,
  "timestamp": "2025-12-17T10:30:45"
}
```

---

### 2. Lấy Reviews của một người (Get Reviews by Reviewee)

**Endpoint**: `GET /api/review/reviewee/{revieweeId}`

**Yêu cầu xác thực**: ❌ Không (Public endpoint)

**Mô tả**: Lấy danh sách tất cả đánh giá cho một người theo ID của họ. Hỗ trợ phân trang và sắp xếp.

#### Request

**URL Parameters**:
```
GET /api/review/reviewee/660e8400-e29b-41d4-a716-446655440001?page=0&size=10&sort=createdAt,desc
```

**Query Parameters** (Optional):

| Parameter | Type | Default | Mô tả |
|-----------|------|---------|-------|
| `page` | Integer | 0 | Số trang (0-indexed) |
| `size` | Integer | 10 | Số lượng kết quả trên một trang |
| `sort` | String | `createdAt,desc` | Sắp xếp theo trường (ví dụ: `rating,asc`) |

#### Response

**Status Code**: `200 OK`

**Success Response**:
```json
{
  "content": [
    {
      "reviewId": "770e8400-e29b-41d4-a716-446655440002",
      "bookingId": "550e8400-e29b-41d4-a716-446655440000",
      "reviewerId": "880e8400-e29b-41d4-a716-446655440003",
      "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
      "rating": 4.5,
      "comment": "Tài xế rất chuyên nghiệp và xe sạch sẽ",
      "createdAt": "2025-12-17T10:30:45"
    },
    {
      "reviewId": "880e8400-e29b-41d4-a716-446655440004",
      "bookingId": "550e8400-e29b-41d4-a716-446655440005",
      "reviewerId": "990e8400-e29b-41d4-a716-446655440006",
      "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
      "rating": 5.0,
      "comment": "Xuất sắc!",
      "createdAt": "2025-12-16T15:20:30"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 2,
  "totalPages": 1,
  "last": true,
  "size": 10,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "numberOfElements": 2,
  "first": true,
  "empty": false
}
```

**Error Responses**:

| Status | Mô tả |
|--------|-------|
| `400` | Invalid revieweeId format (không phải UUID) |
| `404` | Reviewee không tìm thấy |

---

### 3. Lấy Tổng Hợp Xếp Hạng (Get Rating Summary)

**Endpoint**: `GET /api/review/reviewee/{revieweeId}/rating`

**Yêu cầu xác thực**: ❌ Không (Public endpoint)

**Mô tả**: Lấy tóm tắt xếp hạng bao gồm: điểm trung bình, tổng số đánh giá, và phân bố xếp hạng.

#### Request

**URL Parameters**:
```
GET /api/review/reviewee/660e8400-e29b-41d4-a716-446655440001/rating
```

#### Response

**Status Code**: `200 OK`

**Success Response**:
```json
{
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "averageRating": 4.6,
  "totalReviews": 15,
  "ratingDistribution": {
    "1": 0,
    "2": 1,
    "3": 1,
    "4": 5,
    "5": 8
  }
}
```

**Response Fields**:

| Field | Type | Mô tả |
|-------|------|-------|
| `revieweeId` | UUID | ID của người được đánh giá |
| `averageRating` | Double | Điểm xếp hạng trung bình (0.0 - 5.0) |
| `totalReviews` | Integer | Tổng số đánh giá nhận được |
| `ratingDistribution` | Map | Phân bố số lượng đánh giá theo từng sao (1-5) |

**Error Responses**:

| Status | Mô tả |
|--------|-------|
| `400` | Invalid revieweeId format (không phải UUID) |
| `404` | Reviewee không tìm thấy |

---

## Test Cases

### Test Case 1: Tạo Review Thành Công

```
Test ID: TC_CREATE_REVIEW_001
Mô tả: Tạo một review mới với dữ liệu hợp lệ

Tiền điều kiện:
- User đã xác thực (JWT Token hợp lệ)
- User có quyền ROLE_CUSTOMER hoặc ROLE_DRIVER
- Reviewee khác với Reviewer
- Chưa có review nào cho cặp (booking, reviewee) này

Bước:
1. Gửi POST request với body hợp lệ
2. Xác nhận Status Code = 201

Kỳ vọng:
- Response status: 201 Created
- Response body chứa reviewId, createdAt
- rating = giá trị gửi đi
- comment được lưu đúng
```

### Test Case 2: Tạo Review với Rating không hợp lệ

```
Test ID: TC_CREATE_REVIEW_002
Mô tả: Tạo review với rating ngoài khoảng 1.0 - 5.0

Input:
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 6.0,
  "comment": "Test"
}

Kỳ vọng:
- Status Code: 400
- Error Code: INVALID_RATING
```

### Test Case 3: Không thể tự đánh giá

```
Test ID: TC_CREATE_REVIEW_003
Mô tả: Người dùng cố gắng đánh giá chính mình

Tiền điều kiện:
- reviewerId == revieweeId

Kỳ vọng:
- Status Code: 400
- Error Code: CANNOT_REVIEW_SELF
```

### Test Case 4: Review đã tồn tại

```
Test ID: TC_CREATE_REVIEW_004
Mô tả: Tạo review cho cặp (booking, reviewee) đã có review

Tiền điều kiện:
- Đã tồn tại review cho (booking, reviewee) này

Kỳ vọng:
- Status Code: 400
- Error Code: REVIEW_ALREADY_EXISTS
```

### Test Case 5: Lấy Reviews của Reviewee - Phân trang

```
Test ID: TC_GET_REVIEWS_001
Mô tả: Lấy danh sách reviews với phân trang

URL: GET /api/review/reviewee/{revieweeId}?page=0&size=5

Kỳ vọng:
- Status Code: 200
- Response.content.length = 5 (hoặc ít hơn nếu tổng < 5)
- totalPages được tính đúng
```

### Test Case 6: Lấy Rating Summary

```
Test ID: TC_GET_RATING_SUMMARY_001
Mô tả: Lấy tóm tắt xếp hạng của một người

URL: GET /api/review/reviewee/{revieweeId}/rating

Kỳ vọng:
- Status Code: 200
- averageRating giữa 0 - 5.0
- totalReviews >= 0
- ratingDistribution có keys từ 1-5
- Sum(ratingDistribution values) == totalReviews
```

### Test Case 7: Token không hợp lệ

```
Test ID: TC_CREATE_REVIEW_005
Mô tả: Tạo review với token không hợp lệ

Headers:
Authorization: Bearer invalid_token_xyz

Kỳ vọng:
- Status Code: 401
- Message: Token không hợp lệ hoặc hết hạn
```

### Test Case 8: Không có quyền

```
Test ID: TC_CREATE_REVIEW_006
Mô tả: User với ROLE_ADMIN cố tạo review

Headers:
Authorization: Bearer <token_with_ROLE_ADMIN>

Kỳ vọng:
- Status Code: 403
- Message: Access Denied
```

---

## Postman Collection Example

### Environment Variables

```json
{
  "base_url": "http://localhost:8080",
  "jwt_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "reviewer_id": "880e8400-e29b-41d4-a716-446655440003",
  "reviewee_id": "660e8400-e29b-41d4-a716-446655440001",
  "booking_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Create Review Request

```
POST {{base_url}}/api/review

Headers:
Authorization: Bearer {{jwt_token}}
Content-Type: application/json

Body:
{
  "bookingId": "{{booking_id}}",
  "revieweeId": "{{reviewee_id}}",
  "rating": 4.5,
  "comment": "Great driver!"
}
```

### Get Reviews Request

```
GET {{base_url}}/api/review/reviewee/{{reviewee_id}}?page=0&size=10&sort=createdAt,desc
```

### Get Rating Summary Request

```
GET {{base_url}}/api/review/reviewee/{{reviewee_id}}/rating
```

---

## Validation Rules

### Rating Validation
- **Min Value**: 1.0
- **Max Value**: 5.0
- **Type**: Double (decimal accepted)
- **Example Valid Values**: 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0

### Comment Validation
- **Max Length**: 500 characters
- **Required**: No (optional field)
- **Type**: String
- **Special Characters**: Accepted (UTF-8)

### UUID Validation
- **Format**: Standard UUID v4
- **Example**: `550e8400-e29b-41d4-a716-446655440000`

---

## Error Handling

### Standard Error Response Format

```json
{
  "error": "ERROR_CODE",
  "message": "Human readable message",
  "status": 400,
  "timestamp": "2025-12-17T10:30:45"
}
```

### Validation Error Response

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Validation failed",
  "status": 400,
  "violations": [
    {
      "field": "rating",
      "message": "Rating must be between 1.0 and 5.0"
    }
  ],
  "timestamp": "2025-12-17T10:30:45"
}
```

---

## Security Notes

✅ **Authentication**: Tất cả endpoint tạo/update yêu cầu JWT token hợp lệ
✅ **Authorization**: Các endpoint được bảo vệ bằng @PreAuthorize roles
✅ **Rate Limiting**: Áp dụng rate limiting để ngăn spam (cần configure)
✅ **Input Validation**: Tất cả input được validate trước khi xử lý
✅ **CORS**: Được cấu hình để chấp nhận requests từ frontend domain

---

## Notes

- Reviewer ID được lấy tự động từ JWT token, không cần gửi trong request
- Endpoint GET (lấy reviews, rating summary) là public, không cần authentication
- Endpoint POST (tạo review) yêu cầu xác thực và quyền thích hợp
- Datetime format: ISO 8601 (`yyyy-MM-dd'T'HH:mm:ss`)

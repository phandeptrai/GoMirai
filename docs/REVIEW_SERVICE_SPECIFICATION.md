# 📝 Review Service - Technical Specification

**Service Name:** ReviewService  
**Port:** 8088  
**Database:** MongoDB (review_db)  
**Status:** 🚧 Development Phase

---

## 📋 Table of Contents

1. [Domain Model](#1-domain-model)
2. [API Endpoints](#2-api-endpoints)
3. [Request/Response DTOs](#3-requestresponse-dtos)
4. [Database Schema](#4-database-schema)
5. [Security Requirements](#5-security-requirements)
6. [Business Rules](#6-business-rules)
7. [Implementation Checklist](#7-implementation-checklist)
8. [Testing Guide](#8-testing-guide)

---

## 1. Domain Model

### 1.1. Review Entity

**Yêu cầu:**
- Package: `com.gomirai.review.model`
- Collection name: `reviews`
- Sử dụng MongoDB với annotations từ Spring Data MongoDB
- Sử dụng UUID làm primary key (`reviewId`)

**Fields:**
- `reviewId` (UUID, @Id, indexed, unique)
- `bookingId` (UUID, indexed) - ID của booking được review
- `reviewerId` (UUID, indexed) - Người viết review (CUSTOMER hoặc DRIVER)
- `revieweeId` (UUID, indexed) - Người được review (CUSTOMER hoặc DRIVER)
- `rating` (double, indexed) - Điểm đánh giá: 1.0 - 5.0
- `comment` (String) - Nội dung review, tối đa 500 ký tự
- `createdAt` (LocalDateTime) - Thời gian tạo review

**Optional Fields (có thể thêm sau):**
- `updatedAt` (LocalDateTime) - Thời gian cập nhật (nếu có update feature)
- `deleted` (boolean) - Đánh dấu soft delete (nếu có delete feature)

---

## 2. API Endpoints (MVP)

> **MVP Scope:** Chỉ giữ lại các API cần thiết cho hệ thống booking xe:
> - Customer/Driver có thể review nhau sau khi booking completed
> - Hiển thị rating và reviews của driver/customer khi booking

### Base URL
- **Via API Gateway:** `http://localhost:8080/api/review`
- **Direct:** `http://localhost:8088/api/review`

### 2.1. Create Review (MVP Required)

**Endpoint:** `POST /api/review`  
**Authentication:** Required (JWT Token)  
**Authorization:** `ROLE_CUSTOMER` hoặc `ROLE_DRIVER`

**Request Body:**
```json
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 4.5,
  "comment": "Tài xế rất lịch sự và lái xe an toàn. Rất hài lòng!"
}
```

**Response (201 Created):**
```json
{
  "reviewId": "770e8400-e29b-41d4-a716-446655440002",
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "reviewerId": "880e8400-e29b-41d4-a716-446655440003",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 4.5,
  "comment": "Tài xế rất lịch sự và lái xe an toàn. Rất hài lòng!",
  "createdAt": "2025-12-09T10:30:00"
}
```

**Validation Rules:**
- `bookingId`: Required, must be valid UUID
- `revieweeId`: Required, must be valid UUID, cannot be same as reviewerId
- `rating`: Required, must be between 1.0 and 5.0 (inclusive)
- `comment`: Optional, max length 500 characters

**Business Rules:**
- Reviewer phải là người đã tham gia booking (customer hoặc driver)
- Mỗi booking chỉ được review 1 lần cho mỗi người (customer review driver, driver review customer)
- Không thể tự review chính mình

---

### 2.2. Get Reviews by Reviewee ID (MVP Required)

**Endpoint:** `GET /api/review/reviewee/{revieweeId}`  
**Authentication:** Required (JWT Token)  
**Authorization:** Public (any authenticated user)

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 10, max: 50)
- `sort` (optional): Sort field (default: createdAt)
- `direction` (optional): ASC or DESC (default: DESC)

**Path Parameters:**
- `revieweeId`: UUID của người được review (driverId hoặc customerId)

**Query Parameters:**
- `page` (optional): Page number (default: 0)
- `size` (optional): Page size (default: 10, max: 50)
- `sort` (optional): Sort field (default: createdAt)
- `direction` (optional): ASC or DESC (default: DESC)

**Response (200 OK):**
```json
{
  "content": [
    {
      "reviewId": "770e8400-e29b-41d4-a716-446655440002",
      "bookingId": "550e8400-e29b-41d4-a716-446655440000",
      "reviewerId": "880e8400-e29b-41d4-a716-446655440003",
      "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
      "rating": 4.5,
      "comment": "Tài xế rất lịch sự và lái xe an toàn.",
      "createdAt": "2025-12-09T10:30:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}
```

**Use Case:**
- Hiển thị danh sách reviews của driver khi customer chọn driver
- Hiển thị reviews của customer trong driver app

---

### 2.3. Get Average Rating by Reviewee ID (MVP Required)

**Endpoint:** `GET /api/review/reviewee/{revieweeId}/rating`  
**Authentication:** Required (JWT Token)  
**Authorization:** Public (any authenticated user)

**Path Parameters:**
- `revieweeId`: UUID của người được review (driverId hoặc customerId)

**Response (200 OK):**
```json
{
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "averageRating": 4.5,
  "totalReviews": 10,
  "ratingDistribution": {
    "5": 5,
    "4": 3,
    "3": 1,
    "2": 1,
    "1": 0
  }
}
```

**Use Case:**
- Hiển thị rating của driver khi customer chọn driver (ví dụ: ⭐ 4.5 (10 reviews))
- Hiển thị rating summary trong driver/customer profile

---

## 2.4. API Summary (MVP)

| Endpoint | Method | Purpose | MVP Required |
|----------|--------|---------|--------------|
| `/api/review` | POST | Customer/Driver tạo review sau khi booking completed | ✅ **Required** |
| `/api/review/reviewee/{revieweeId}` | GET | Lấy danh sách reviews của driver/customer (để hiển thị khi booking) | ✅ **Required** |
| `/api/review/reviewee/{revieweeId}/rating` | GET | Lấy rating summary (để hiển thị rating khi chọn driver) | ✅ **Required** |

**Các API không cần cho MVP (có thể implement sau):**
- ❌ `GET /api/review/{reviewId}` - Không cần thiết
- ❌ `GET /api/review/booking/{bookingId}` - Không cần thiết
- ❌ `PUT /api/review/{reviewId}` - Không cần cho MVP
- ❌ `DELETE /api/review/{reviewId}` - Không cần cho MVP
- ❌ `GET /api/review/admin/all` - Không cần cho MVP

---

## 3. Request/Response DTOs

### 3.1. CreateReviewRequest

**Yêu cầu:**
- Package: `com.gomirai.review.dto.request`
- Sử dụng Jakarta Validation annotations

**Fields:**
- `bookingId` (UUID, @NotNull) - Required
- `revieweeId` (UUID, @NotNull) - Required
- `rating` (Double, @NotNull, @DecimalMin(1.0), @DecimalMax(5.0)) - Required, range 1.0-5.0
- `comment` (String, @Size(max=500)) - Optional, max 500 characters

### 3.2. ReviewResponse

**Yêu cầu:**
- Package: `com.gomirai.review.dto.response`
- Sử dụng Jackson annotations cho date formatting

**Fields:**
- `reviewId` (UUID)
- `bookingId` (UUID)
- `reviewerId` (UUID)
- `revieweeId` (UUID)
- `rating` (Double)
- `comment` (String)
- `createdAt` (LocalDateTime, format: "yyyy-MM-dd'T'HH:mm:ss")

### 3.3. RatingSummaryResponse

**Yêu cầu:**
- Package: `com.gomirai.review.dto.response`

**Fields:**
- `revieweeId` (UUID)
- `averageRating` (Double) - Điểm trung bình
- `totalReviews` (Integer) - Tổng số reviews
- `ratingDistribution` (Map<Integer, Integer>) - Key: rating (1-5), Value: số lượng reviews

### 3.4. Pagination Response

**Yêu cầu:**
- Sử dụng `Page<T>` từ Spring Data hoặc tạo custom `PageResponse<T>` nếu cần
- Fields: `content`, `page`, `size`, `totalElements`, `totalPages`

---

## 4. Database Schema

### 4.1. MongoDB Collection: `reviews`

**Database:** `review_db`

**Indexes Required:**
- Single field indexes:
  - `reviewId` (unique)
  - `bookingId`
  - `reviewerId`
  - `revieweeId`
  - `createdAt` (descending)
- Compound indexes:
  - `bookingId + reviewerId` (unique) - Đảm bảo mỗi booking chỉ được review 1 lần cho mỗi người
  - `revieweeId + createdAt` (descending) - Để query reviews theo reviewee với sort

**Auto-index Creation:**
- Sử dụng `spring.data.mongodb.auto-index-creation=true` để tự động tạo indexes từ entity annotations

**Document Structure:**
- Primary key: `reviewId` (UUID)
- Fields: bookingId, reviewerId, revieweeId, rating, comment, createdAt
- Optional: updatedAt, deleted (nếu có update/delete feature)

---

## 5. Security Requirements

### 5.1. Authentication
- Tất cả endpoints (trừ health check) yêu cầu JWT token
- Token được validate qua `JwtAuthenticationFilter` từ common-lib

### 5.2. Authorization Rules (MVP)

| Endpoint | Method | Required Role | Additional Rules |
|----------|--------|---------------|------------------|
| `/api/review` | POST | `ROLE_CUSTOMER` \| `ROLE_DRIVER` | Reviewer phải là người tham gia booking |
| `/api/review/reviewee/{revieweeId}` | GET | Any authenticated | Public - để hiển thị reviews khi booking |
| `/api/review/reviewee/{revieweeId}/rating` | GET | Any authenticated | Public - để hiển thị rating khi chọn driver |

### 5.3. Security Implementation Requirements

**SecurityConfig Requirements:**
- Package: `com.gomirai.review.config`
- Sử dụng `JwtAuthenticationFilter` và `JwtAuthenticationEntryPoint` từ common-lib
- Inject từ common-lib, không cần implement lại
- Cấu hình:
  - Disable CSRF (stateless API)
  - Session management: STATELESS
  - CORS configuration từ application.properties
  - Permit `/actuator/health` endpoint
  - Require authentication cho tất cả endpoints khác
  - Add JWT filter trước UsernamePasswordAuthenticationFilter

**Controller Requirements:**
- Package: `com.gomirai.review.controller`
- Inject `SecurityUtils` từ common-lib để lấy current user ID
- Sử dụng `@PreAuthorize` annotations cho authorization
- Sử dụng `@Valid` cho request validation
- **KHÔNG CẦN** tạo exception handler riêng - `GlobalExceptionHandler` từ common-lib tự động xử lý:
  - `BusinessException` → 400 Bad Request
  - `NotFoundException` → 404 Not Found
  - `ForbiddenException` → 403 Forbidden
  - `UnauthorizedException` → 401 Unauthorized
  - `MethodArgumentNotValidException` → 400 Bad Request với ValidationErrorResponse

---

## 6. Business Rules

### 6.1. Review Creation Rules
1. ✅ Reviewer phải là customer hoặc driver của booking
2. ✅ Mỗi booking chỉ được review 1 lần cho mỗi người (customer → driver, driver → customer)
3. ✅ Không thể tự review chính mình (reviewerId ≠ revieweeId)
4. ✅ Rating phải trong khoảng 1.0 - 5.0
5. ✅ Comment tối đa 500 ký tự

### 6.2. Review Update Rules (Not Required for MVP)
> Update và Delete không cần cho MVP. Có thể implement sau nếu cần.

### 6.3. Review Deletion Rules (Not Required for MVP)
> Update và Delete không cần cho MVP. Có thể implement sau nếu cần.

### 6.4. Rating Calculation Rules
1. ✅ Average rating = tổng rating / số lượng reviews (chưa bị xóa)
2. ✅ Rating distribution đếm số lượng reviews theo từng mức (1, 2, 3, 4, 5)
3. ✅ Chỉ tính các reviews chưa bị xóa

---

## 7. Implementation Checklist

### 7.0. Project Structure

Cấu trúc thư mục phải giống PricingService:

```
ReviewService/
├── .mvn/
│   └── wrapper/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── gomirai/
│   │   │           └── review/
│   │   │               ├── ReviewServiceApplication.java
│   │   │               ├── config/
│   │   │               │   ├── MongoConfiguration.java
│   │   │               │   ├── ObjectIdToUuidConverter.java
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   └── UuidToObjectIdConverter.java
│   │   │               ├── controller/
│   │   │               │   ├── AdminReviewController.java
│   │   │               │   └── ReviewController.java
│   │   │               ├── dto/
│   │   │               │   ├── request/
│   │   │               │   │   ├── CreateReviewRequest.java
│   │   │               │   │   └── UpdateReviewRequest.java
│   │   │               │   └── response/
│   │   │               │       ├── RatingSummaryResponse.java
│   │   │               │       └── ReviewResponse.java
│   │   │               ├── exception/
│   │   │               │   └── ReviewErrorCode.java
│   │   │               ├── model/
│   │   │               │   └── Review.java
│   │   │               ├── repository/
│   │   │               │   └── ReviewRepository.java
│   │   │               └── service/
│   │   │                   └── ReviewService.java
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/
│           └── com/
│               └── gomirai/
│                   └── review/
│                       └── ReviewServiceApplicationTests.java
├── Dockerfile
├── mvnw
├── mvnw.cmd
└── pom.xml
```

### 7.1. Project Setup
- [ ] Tạo ReviewService module trong project root
- [ ] Copy cấu trúc từ PricingService làm template
- [ ] Thêm dependencies vào `pom.xml`:
  - `gomirai-common-lib` (version 1.0.1) - **BẮT BUỘC**
  - `spring-boot-starter-data-mongodb`
  - `spring-boot-starter-security`
  - `spring-boot-starter-validation`
  - `spring-cloud-starter-consul-discovery`
  - `spring-kafka` (optional, nếu cần events)
  - `spring-boot-starter-actuator`
- [ ] Tạo `ReviewServiceApplication.java`:
  - Package: `com.gomirai.review`
  - Annotations: `@SpringBootApplication`, `@EnableDiscoveryClient`
  - `@ComponentScan` phải include: `com.gomirai.review` và `com.gomirai.common` (BẮT BUỘC)
- [ ] Tạo `application.properties` với cấu hình chuẩn GoMirai (xem section 7.7)
- [ ] Port: 8088

### 7.2. Domain Layer
- [ ] Tạo `Review` entity với MongoDB annotations (xem section 1.1)
- [ ] Tạo `ReviewRepository` interface extends `MongoRepository<Review, UUID>`
- [ ] Tạo custom query methods cần thiết:
  - `findByReviewId(UUID reviewId)` - Optional<Review>
  - `findByBookingId(UUID bookingId)` - List<Review>
  - `findByRevieweeIdAndDeletedFalse(UUID revieweeId, Pageable)` - Page<Review>
  - `existsByBookingIdAndReviewerId(UUID bookingId, UUID reviewerId)` - boolean
  - `countByRevieweeIdAndDeletedFalse(UUID revieweeId)` - long
- [ ] Tạo MongoDB Configuration với UUID converters
- [ ] Copy `ObjectIdToUuidConverter` và `UuidToObjectIdConverter` từ PricingService
- [ ] Tạo indexes trong MongoDB (sẽ tự động tạo nếu `auto-index-creation=true`)

### 7.3. DTO Layer
- [ ] `CreateReviewRequest` với validation annotations (xem section 3.1)
- [ ] `ReviewResponse` với Jackson annotations (xem section 3.3)
- [ ] `RatingSummaryResponse` (xem section 3.4)
- [ ] **KHÔNG CẦN** `PageResponse<T>` cho MVP - sử dụng `Page<T>` từ Spring Data
- [ ] Tạo `ReviewErrorCode` class (pattern giống PricingErrorCode):
  - Package: `com.gomirai.review.exception`
  - Final class với static final String constants
  - Error codes cần thiết:
    - `REVIEW_NOT_FOUND`
    - `REVIEW_ALREADY_EXISTS`
    - `CANNOT_REVIEW_SELF`
    - `INVALID_RATING`

### 7.4. Service Layer (MVP - Chỉ implement 3 methods)
- [ ] `ReviewService` interface
- [ ] `ReviewServiceImpl` implementation
- [ ] **MVP Required Methods:**
  - [ ] `createReview(CreateReviewRequest, UUID reviewerId)` ✅
  - [ ] `getReviewsByRevieweeId(UUID revieweeId, Pageable)` ✅
  - [ ] `getRatingSummary(UUID revieweeId)` ✅
- [ ] **Not Required for MVP (implement sau nếu cần):**
  - [ ] `getReviewById(UUID reviewId)` ❌
  - [ ] `getReviewsByBookingId(UUID bookingId)` ❌
  - [ ] `updateReview(...)` ❌
  - [ ] `deleteReview(...)` ❌
- [ ] Business logic validation:
  - Validate reviewerId ≠ revieweeId (không thể tự review chính mình)
  - Validate không duplicate review (bookingId + reviewerId)
  - Validate rating trong khoảng 1.0 - 5.0
- [ ] Sử dụng exceptions từ common-lib:
  - `BusinessException` cho business logic errors (duplicate, invalid data)
  - `NotFoundException` cho resource not found (nếu cần)
  - `ForbiddenException` cho authorization errors (nếu cần)
- [ ] Service methods cần implement:
  - `createReview(CreateReviewRequest, UUID reviewerId)` - Validate và tạo review
  - `getReviewsByRevieweeId(UUID revieweeId, Pageable)` - Query với pagination
  - `getRatingSummary(UUID revieweeId)` - Tính toán average rating và distribution

### 7.5. Controller Layer (MVP - Chỉ 1 controller)
- [ ] `ReviewController` với **3 endpoints MVP:**
  - [ ] `POST /api/review` - Create review ✅
  - [ ] `GET /api/review/reviewee/{revieweeId}` - Get reviews ✅
  - [ ] `GET /api/review/reviewee/{revieweeId}/rating` - Get rating summary ✅
- [ ] **KHÔNG CẦN** `AdminReviewController` cho MVP
- [ ] `@PreAuthorize` annotations cho authorization
- [ ] `@Valid` annotations cho request validation
- [ ] **KHÔNG CẦN** exception handler riêng - `GlobalExceptionHandler` từ common-lib tự động xử lý
- [ ] Inject `SecurityUtils` từ common-lib để lấy current user ID
- [ ] Controller endpoints cần implement:
  - `POST /api/review` - Create review, sử dụng `@PreAuthorize("hasAnyRole('CUSTOMER', 'DRIVER')")`
  - `GET /api/review/reviewee/{revieweeId}` - Get reviews với pagination
  - `GET /api/review/reviewee/{revieweeId}/rating` - Get rating summary
- [ ] Sử dụng `@Valid` cho request validation
- [ ] Return `ResponseEntity` với appropriate HTTP status codes

### 7.6. Security Configuration
- [ ] `SecurityConfig` với JWT filter từ common-lib (xem section 5.3)
- [ ] CORS configuration (copy từ PricingService)
- [ ] Permit health check endpoint (`/actuator/health`)
- [ ] Require authentication cho tất cả endpoints khác
- [ ] **KHÔNG CẦN** tạo exception handler riêng - `GlobalExceptionHandler` từ common-lib tự động xử lý:
  - `BusinessException` → 400 Bad Request
  - `NotFoundException` → 404 Not Found
  - `ForbiddenException` → 403 Forbidden
  - `UnauthorizedException` → 401 Unauthorized
  - `MethodArgumentNotValidException` → 400 Bad Request với ValidationErrorResponse

### 7.7. Configuration
- [ ] `application.properties` - Copy từ PricingService và sửa các giá trị:
  - `spring.application.name=ReviewService`
  - `server.port=8088`
  - `spring.data.mongodb.database=review_db`
  - `spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}`
  - Consul configuration (giống PricingService)
  - Kafka configuration (nếu cần events)
  - CORS configuration (giống PricingService)
  - Actuator configuration (chỉ expose health)
  - JWT secret: `security.jwt.secret=${SECURITY_JWT_SECRET}`
  - Logging levels
- [ ] Thêm `REVIEW_MONGODB_URI` vào `.env` file
- [ ] Thêm `REVIEW_MONGODB_URI` vào `env.example`

### 7.8. API Gateway Configuration
- [ ] Thêm routing trong `ProxyController.java`:
  - Trong method `mapServiceName()`: thêm case `"review"` và `"reviews"` → return `"ReviewService"`
  - Trong method `getServicePathPrefix()`: thêm case `"review"` và `"reviews"` → return `"/api/review"`

### 7.9. Docker Configuration
- [ ] Tạo `Dockerfile` cho ReviewService (copy từ PricingService và sửa):
  ```dockerfile
  # Multi-stage build with common-lib support
  FROM eclipse-temurin:21-jdk-alpine AS build
  WORKDIR /workspace
  
  # Copy common lib first and build it
  COPY gomirai-common-lib ./gomirai-common-lib
  WORKDIR /workspace/gomirai-common-lib
  RUN apk add --no-cache maven && mvn clean install -DskipTests
  
  # Now build ReviewService
  WORKDIR /workspace/ReviewService
  COPY ReviewService/pom.xml .
  COPY ReviewService/mvnw .
  COPY ReviewService/.mvn ./.mvn
  RUN ./mvnw dependency:go-offline -B
  
  # Copy source and build
  COPY ReviewService/src ./src
  RUN ./mvnw clean package -DskipTests
  
  # Runtime stage
  FROM eclipse-temurin:21-jre-alpine
  WORKDIR /app
  COPY --from=build /workspace/ReviewService/target/*.jar app.jar
  EXPOSE 8088
  ENTRYPOINT ["java", "-jar", "app.jar"]
  ```
- [ ] Thêm service vào `docker-compose.yml`:
  ```yaml
  # Review Service
  review-service:
    build:
      context: .
      dockerfile: ReviewService/Dockerfile
    container_name: review-service
    expose:
      - "8088"  # Chỉ expose trong internal network
    depends_on:
      consul:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_DATA_MONGODB_URI: ${REVIEW_MONGODB_URI}
      SECURITY_JWT_SECRET: ${JWT_SECRET}
      SPRING_CLOUD_CONSUL_HOST: consul
      SPRING_CLOUD_CONSUL_PORT: 8500
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      SERVER_PORT: 8088
      SPRING_PROFILES_ACTIVE: dev
    networks:
      - gomirai-network
    restart: on-failure
  ```

### 7.10. Testing
- [ ] Unit tests cho Service layer
- [ ] Integration tests cho Controller layer
- [ ] Test với Postman/curl
- [ ] Test security và authorization
- [ ] Test validation rules

---

## 8. Testing Guide

### 8.1. Prerequisites
1. MongoDB đang chạy và có database `review_db`
2. Consul đang chạy
3. API Gateway đang chạy
4. Có JWT token hợp lệ với các roles:
   - CUSTOMER
   - DRIVER
   - ADMIN

### 8.2. Test Cases (MVP - Chỉ 4 test cases)

#### Test 1: Create Review (Customer) ✅ MVP Required
```bash
POST http://localhost:8080/api/review
Authorization: Bearer <customer-jwt-token>
Content-Type: application/json

{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 4.5,
  "comment": "Tài xế rất lịch sự và lái xe an toàn."
}
```

**Expected:** 201 Created với ReviewResponse

#### Test 2: Create Duplicate Review (Should Fail) ✅ MVP Required
```bash
POST http://localhost:8080/api/review
Authorization: Bearer <customer-jwt-token>
Content-Type: application/json

{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "revieweeId": "660e8400-e29b-41d4-a716-446655440001",
  "rating": 5.0,
  "comment": "Lần 2"
}
```

**Expected:** 400 Bad Request - "Review already exists for this booking"

#### Test 3: Get Review by ID
```bash
GET http://localhost:8080/api/review/770e8400-e29b-41d4-a716-446655440002
Authorization: Bearer <any-jwt-token>
```

**Expected:** 200 OK với ReviewResponse

#### Test 4: Get Reviews by Booking ID
```bash
GET http://localhost:8080/api/review/booking/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <any-jwt-token>
```

**Expected:** 200 OK với List<ReviewResponse>

#### Test 5: Get Reviews by Reviewee ID (Paginated)
```bash
GET http://localhost:8080/api/review/reviewee/660e8400-e29b-41d4-a716-446655440001?page=0&size=10&sort=createdAt&direction=DESC
Authorization: Bearer <any-jwt-token>
```

**Expected:** 200 OK với Page<ReviewResponse>

#### Test 4: Get Rating Summary ✅ MVP Required
```bash
GET http://localhost:8080/api/review/reviewee/660e8400-e29b-41d4-a716-446655440001/rating
Authorization: Bearer <any-jwt-token>
```

**Expected:** 200 OK với RatingSummaryResponse

### 8.3. Postman Collection

**Yêu cầu:**
- Tạo Postman collection với 4 test cases MVP
- Environment variables: `base_url`, `customer_token`, `driver_token`
- Pre-request scripts để tự động set Authorization header
- Tests để validate responses (status code, response structure)

---

## 9. Additional Notes

### 9.1. Event-Driven Integration (Optional - Not Required for MVP)
Nếu cần publish events khi review được tạo (có thể implement sau):

**Kafka Topics:**
- `review-created-event` (nếu cần)

**Event Structure:**
- Extend từ `BaseEvent` trong common-lib
- Include: reviewId, bookingId, reviewerId, revieweeId, rating
- Event type: `REVIEW_CREATED`

### 9.2. Integration với Booking Service (MVP)
- **Flow:** Sau khi booking status = COMPLETED → Customer/Driver có thể review nhau
- **Option 1:** Booking Service gọi Review Service API khi booking completed
- **Option 2:** Frontend gọi Review Service API trực tiếp sau khi booking completed
- **Option 3:** Booking Service publish Kafka event → Review Service consume (nếu cần async)
- **MVP Recommendation:** Option 2 (Frontend gọi trực tiếp) - đơn giản nhất

### 9.3. Performance Considerations
- Sử dụng indexes cho các queries thường dùng
- Cache rating summary nếu cần (Redis)
- Pagination cho list endpoints
- Consider read replicas nếu traffic cao

---

## 10. References

- [GoMirai Common Library README](../gomirai-common-lib/README.md)
- [Adding New Service Guide](./ADDING_NEW_SERVICE.md)
- [Project Overview](./PROJECT_OVERVIEW.md)
- [Pricing Service Example](../PricingService/)

---

**Last Updated:** 2025-12-09  
**Author:** GoMirai Team  
**Status:** 📝 Specification Ready for Implementation




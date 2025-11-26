# GoMirai Service Expansion Roadmap

Hi team! AuthService và UserService đã hoàn tất, dưới đây là kế hoạch triển khai các service còn lại, mỗi service tương ứng với một domain. Lộ trình ưu tiên dựa trên mức độ ảnh hưởng tới luồng đặt xe end-to-end.

## 1. DriverService (Driver Domain)
- **Scope:** Quản lý hồ sơ tài xế, trạng thái hoạt động, phương tiện.
- **Core models:** `DriverProfile`, `DriverVehicle`, `DriverAccountStatus`, `VehicleType`.
- **Responsibilities:**
  - CRUD hồ sơ tài xế và phương tiện.
  - Đồng bộ trạng thái tài xế với TrackingService.
  - Tính điểm rating trung bình từ ReviewService (read model).
- **Tech notes:** Spring Boot + PostgreSQL; publish driver-status events qua Kafka.
- **Dependencies:** UserService (liên kết userId), TrackingService (event sync).
- **Milestones:**
  1. API CRUD + validation license.
  2. Vehicle management + plate validation.
  3. Event integration (driver onboarding, suspension).

## 2. BookingService (Booking Domain)
- **Scope:** Vòng đời chuyến đi (request → assign → complete/cancel).
- **Core models:** `Booking`, `BookingPriceSnapshot`, `AddressSnapshot`, `BookingStatus`.
- **Responsibilities:**
  - Nhận request đặt xe, gọi PricingService lấy giá ước tính.
  - Giữ trạng thái booking và cập nhật theo tiến trình.
  - Gửi sự kiện cho PaymentService và NotificationService.
- **Tech notes:** Spring Boot + PostgreSQL, Redis cho giữ booking tạm; sử dụng Saga/Outbox.
- **Dependencies:** DriverService, PricingService, PaymentService, NotificationService.
- **Milestones:**
  1. Create booking + status machine.
  2. Matching driver cơ bản (round-robin / nearest).
  3. Integration với Payment + Notification.

## 3. PricingService (Pricing Domain)
- **Scope:** Tính giá động dựa trên rule & surge.
- **Core models:** `PricingRule`, `PricingResponse`, `Money`.
- **Responsibilities:**
  - CRUD pricing rules theo region/vehicleType.
  - API tính giá cho BookingService.
  - Cập nhật surge multiplier theo dữ liệu Tracking/Booking.
- **Tech notes:** Spring Boot + PostgreSQL; có thể cache rule bằng Redis.
- **Dependencies:** BookingService (caller), TrackingService (future for surge).
- **Milestones:**
  1. Rule CRUD + validation.
  2. Pricing API + unit tests cho công thức.
  3. Surge updater (schedule job).

## 4. PaymentService (Payment Domain)
- **Scope:** Ví người dùng, giao dịch.
- **Core models:** `Wallet`, `Transaction`, `TransactionType`, `PaymentStatus`.
- **Responsibilities:**
  - Quản lý số dư ví, top-up, withdrawal.
  - Nhận sự kiện từ BookingService để trừ tiền.
  - Webhook/adapter với cổng thanh toán ngoài (tạm stub).
- **Tech notes:** Spring Boot + PostgreSQL + Kafka events; cần idempotency layer.
- **Dependencies:** BookingService, NotificationService.
- **Milestones:**
  1. Wallet CRUD + balance lock.
  2. Transaction processing + idempotent handlers.
  3. External payment adapter + notification hooks.

## 5. NotificationService (Notification Domain)
- **Scope:** Push/SMS/email thông báo.
- **Core models:** `UserDeviceToken`, `Notification`, `NotificationType`, `NotificationChannel`.
- **Responsibilities:**
  - Quản lý device token (Firebase).
  - Nhận event từ Booking/Payment và gửi push/email.
  - Theo dõi trạng thái đã đọc.
- **Tech notes:** Spring Boot + MongoDB (notification log), Firebase SDK.
- **Dependencies:** AuthService (userId auth), BookingService, PaymentService.
- **Milestones:**
  1. Device token API (register/remove).
  2. Push notification worker.
  3. Template + preference management.

## 6. TrackingService (Tracking Domain)
- **Scope:** Vị trí realtime tài xế, tìm kiếm tài xế gần nhất.
- **Core models:** `DriverGeoState`, `NearbyDriverRequest`, `GeoPoint`.
- **Responsibilities:**
  - Subscribe vị trí từ driver app (WebSocket/MQTT).
  - Cung cấp API tìm driver gần nhất cho BookingService.
  - Sync trạng thái với DriverService.
- **Tech notes:** Spring Boot + Redis/MongoGeo, WebSocket gateway cho driver app.
- **Dependencies:** DriverService, BookingService.
- **Milestones:**
  1. WebSocket ingest + persist geo state.
  2. Nearby search API (Geo radius query).
  3. Online/offline events cho Booking/Notification.

## 7. MapService (Map Domain)
- **Scope:** Tính tuyến đường, distance matrix.
- **Core models:** `RouteRequest`, `RouteResponse`, `GeoPoint`.
- **Responsibilities:**
  - Proxy sang provider (Google/Mapbox) để lấy route.
  - Cache polyline và distance cho Pricing/Booking.
  - Cung cấp health metrics (API quota).
- **Tech notes:** Spring Boot + Redis cache; external API integration.
- **Dependencies:** BookingService, PricingService.
- **Milestones:**
  1. Route API proxy + caching.
  2. Distance matrix batch job.
  3. Alerting on quota/errors.

## 8. ReviewService (Review Domain)
- **Scope:** Đánh giá giữa khách và tài xế.
- **Core models:** `Review`, `Rating`.
- **Responsibilities:**
  - Lưu review sau chuyến đi (booking completed).
  - Tính điểm trung bình cho DriverService/UserService (read model).
  - Moderation queue (simple).
- **Tech notes:** Spring Boot + PostgreSQL; Kafka consumer từ Booking events.
- **Dependencies:** BookingService, DriverService.
- **Milestones:**
  1. Review submission API + validation.
  2. Rating aggregation per driver/customer.
  3. Moderation flags + admin tooling.

## Ưu tiên triển khai & phân công
- **Dev A (Senior):**
  1. DriverService → BookingService (liên quan nhiều domain logic phức tạp).
  2. PaymentService → TrackingService (yêu cầu hiểu sâu event-driven & concurrency).
  3. ReviewService (kết nối nhiều service và cần chiến lược dữ liệu).
- **Dev B (Mid-Level):**
  1. PricingService (quy tắc rõ, dễ test đơn vị).
  2. NotificationService (làm việc với device token & template).
  3. MapService (tập trung tích hợp external API + caching).

## Hành động tiếp theo
- Dev A kickoff DriverService sprint (thiết kế DB + API spec chi tiết).
- Dev B chuẩn bị PricingService rule CRUD & contract với BookingService.
- Cả hai phối hợp chuẩn hóa event schema (Kafka Avro/JSON) và mở rộng docker-compose (Redis, MongoDB).

Hãy update file này sau mỗi sprint để phản ánh tiến độ và thay đổi ưu tiên. Thanks!


# AuthService - Thay đổi đã thực hiện

## Mục tiêu
- Quản lý xác thực & phân quyền, cấp phát/kiểm tra JWT, dễ tích hợp với các service khác.

## Các thay đổi chính

- pom.xml
  - Thêm: spring-boot-starter-validation, jjwt (api/impl/jackson) cho JWT.

- Cấu hình (`src/main/resources/application.properties`)
  - Thêm `security.jwt.secret`, `security.jwt.access-ttl-ms`.
  - Thêm `kafka.topic.user-registered=user-registered`.

- Model & Repository
  - `model/AuthUser.java`: Document Mongo, trường `userId: UUID`, `phoneNumber`, `passwordHash`, `provider`, `role`, `createdAt`.
  - `model/AuthProvider.java`, `model/Role.java`.
  - `repository/AuthUserRepository.java`: CRUD + truy vấn theo `phoneNumber`.

- DTO
  - `dto/RegisterRequest.java`, `dto/LoginRequest.java` (validation theo yêu cầu).
  - `dto/AuthResponse.java`, `dto/TokenValidationResponse.java`.

- Bảo mật & JWT
  - `security/JwtService.java`: Sinh & parse JWT HS256.
  - `security/JwtAuthenticationFilter.java`: Đọc Bearer token, set SecurityContext.
  - `config/SecurityConfig.java`: Stateless, mở `/auth/**`, `/actuator/**`.

- Kafka
  - `events/UserRegisteredEvent.java`.
  - `messaging/UserEventsProducer.java`: Gửi event lên topic `user-registered`.

- Nghiệp vụ & API
  - `service/AuthApplicationService.java`: register/login/validate.
  - `controller/AuthController.java`:
    - `POST /auth/register`: trả `AuthResponse` (201).
    - `POST /auth/login`: trả `AuthResponse` (200).
    - `POST /auth/validate`: trả `TokenValidationResponse`.

- Dockerfile
  - JDK 21, build nhiều tầng, expose 8081.

## Kiến trúc & Clean code
- Phân tầng rõ: controller -> service -> repository, tách DTO/Model.
- Validation ở DTO, trả JSON chuẩn, handler lỗi 400 cho `IllegalArgumentException`.
- JWT tách thành service riêng, filter mỏng, Security cấu hình tối thiểu.
- Kafka producer cô lập, dùng topic qua cấu hình.

## Ghi chú
- Lưu trữ MongoDB (phù hợp với docker-compose hiện tại), nhưng giữ cấu trúc trường đúng yêu cầu.
- OAuth Google/Facebook: đã sẵn `spring-boot-starter-oauth2-client` làm placeholder, sẽ bổ sung flow sau.




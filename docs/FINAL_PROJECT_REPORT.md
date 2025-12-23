# BÁO CÁO ĐỒ ÁN TỐT NGHIỆP: HỆ THỐNG MICROSERVICES ĐẶT XE TRỰC TUYẾN (GOMIRAI)

**Sinh viên thực hiện:** [Tên của bạn]  
**Mã số sinh viên:** [MSSV]  
**Giảng viên hướng dẫn:** [Tên GVHD]  

---

## MỤC LỤC

1.  **CHƯƠNG 1: TỔNG QUAN VỀ ĐỀ TÀI**
    1.1. Đặt vấn đề
    1.2. Lý do chọn đề tài
    1.3. Mục tiêu và Phạm vi nghiên cứu
    1.4. Công nghệ sử dụng

2.  **CHƯƠNG 2: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG**
    2.1. Phân tích yêu cầu (Use Cases)
    2.2. Kiến trúc tổng thể (System Architecture)
    2.3. Thiết kế Cơ sở dữ liệu (Database Design)
    2.4. Thiết kế giao tiếp giữa các dịch vụ (Inter-service Communication)

3.  **CHƯƠNG 3: GIẢI PHÁP CÔNG NGHỆ VÀ HIỆN THỰC**
    3.1. Cấu trúc tổ chức dự án (Multi-module)
    3.2. Xây dựng thư viện dùng chung (Gomirai Common Lib)
    3.3. Hiện thực các Service cốt lõi
    3.4. Giải quyết bài toán Real-time (WebSocket Hybrid Architecture)
    3.5. Quản lý giao dịch phân tán (Saga Pattern)

4.  **CHƯƠNG 4: KẾT QUẢ THỰC NGHIỆM**
    4.1. Quy trình triển khai (Deployment)
    4.2. Demo các chức năng chính
    4.3. Đánh giá hiệu năng và tính ổn định

5.  **CHƯƠNG 5: TỔNG KẾT VÀ HƯỚNG PHÁT TRIỂN**
    5.1. Kết luận
    5.2. Hạn chế
    5.3. Hướng phát triển

---

## CHƯƠNG 1: TỔNG QUAN VỀ ĐỀ TÀI

### 1.1. Đặt vấn đề
Trong kỷ nguyên số, nhu cầu di chuyển và vận tải hành khách tăng trưởng mạnh mẽ, kéo theo sự bùng nổ của các ứng dụng đặt xe công nghệ (Ride-hailing apps) như Grab, Uber, Gojek. Đặc điểm của các hệ thống này là khối lượng giao dịch khổng lồ, yêu cầu phản hồi thời gian thực (real-time) và tính sẵn sàng cao (High Availability).

Các kiến trúc nguyên khối (Monolithic) truyền thống thường gặp khó khăn trong việc mở rộng (scaling) từng phần riêng biệt (ví dụ: chỉ scale phần đặt xe mà không cần scale phần quản lý user), cũng như gặp rủi ro cao khi một lỗi nhỏ có thể làm ngưng trệ toàn bộ hệ thống.

### 1.2. Lý do chọn đề tài và Giải pháp đề xuất: GoMirai
Để giải quyết các vấn đề trên, đồ án này đề xuất xây dựng hệ thống **GoMirai** dựa trên kiến trúc **Microservices**.
*   **Tính linh hoạt:** Tách biệt các nghiệp vụ thành các dịch vụ độc lập (Auth, Booking, Driver...).
*   **Khả năng chịu lỗi (Fault Tolerance):** Một service chết không làm sập toàn bộ hệ thống.
*   **Hiệu năng cao:** Sử dụng kiến trúc hướng sự kiện (Event-Driven Architecture) giúp xử lý bất đồng bộ, giảm độ trễ cho người dùng cuối.

### 1.3. Công nghệ sử dụng
Hệ thống được xây dựng trên nền tảng công nghệ hiện đại và phổ biến trong doanh nghiệp:
*   **Ngôn ngữ & Framework:** Java 21, Spring Boot 3.5.7.
*   **Hạ tầng Microservices:** Spring Cloud Gateway, Spring Cloud Consul (Service Discovery).
*   **Message Broker:** Apache Kafka (Xử lý giao tiếp bất đồng bộ).
*   **Database:** MongoDB (NoSQL Database - Database per Service pattern).
*   **Containerization:** Docker & Docker Compose.
*   **Real-time Communication:** WebSocket (STOMP protocol).

---

## CHƯƠNG 2: PHÂN TÍCH VÀ THIẾT KẾ HỆ THỐNG

### 2.1. Phân tích yêu cầu (Use Cases)
Hệ thống phục vụ 3 nhóm đối tượng chính:

1.  **Khách hàng (Customer):**
    *   Đăng ký/Đăng nhập.
    *   Đặt chuyến xe (Booking): Nhập điểm đón/đến, xem giá dự kiến.
    *   Theo dõi tài xế trên bản đồ thời gian thực.
    *   Xem lịch sử chuyến đi.

2.  **Tài xế (Driver):**
    *   Đăng ký đối tác & Gửi hồ sơ duyệt (Bằng lái, Xe).
    *   Bật/Tắt trạng thái hoạt động (Online/Offline).
    *   Nhận thông báo chuyến xe mới (Popup Offer).
    *   Chấp nhận/Từ chối chuyến.
    *   Cập nhật trạng thái chuyến (Đã đến, Đang đi, Hoàn thành).

3.  **Quản trị viên (Admin):**
    *   Xem danh sách tài xế chờ duyệt.
    *   Duyệt hoặc từ chối hồ sơ tài xế.

### 2.2. Kiến trúc tổng thể (System Architecture)
GoMirai áp dụng mô hình kiến trúc Microservices với các thành phần chính:

*   **API Gateway (Port 8080):** Cổng vào duy nhất (Single Entry Point) cho Khách hàng. Chịu trách nhiệm Routing, Authentication (cơ bản), Rate Limiting.
*   **Auth Service:** Quản lý danh tính, cấp phát JWT Token.
*   **User Service:** Quản lý thông tin cá nhân khách hàng.
*   **Driver Service:** Quản lý hồ sơ tài xế, trạng thái online/offline, vị trí tài xế. Tối ưu hóa cho việc truy xuất nhanh.
*   **Booking Service:** Core engine xử lý logic đặt xe, tính giá, điều phối trạng thái chuyến đi.
*   **Notification Service (Tương lai):** Gửi email/SMS/Push Noti.

**Luồng dữ liệu (Data Flow):**
*   Giao tiếp **Synchronous (REST)** giữa Client và Gateway.
*   Giao tiếp **Asynchronous (Kafka Event)** giữa các Microservices để đảm bảo tính Decoupling (Giảm sự phụ thuộc).

### 2.3. Thiết kế Cơ sở dữ liệu (Database Design)
Áp dụng pattern **Database per Service** để đảm bảo tính độc lập dữ liệu. Mỗi service sở hữu một database riêng biệt, không service nào được truy cập trực tiếp database của service khác.

1.  **Auth DB (MongoDB):**
    *   `users`: Lưu credentials (username, password hash, role).
2.  **User DB (MongoDB):**
    *   `user_profiles`: Lưu thông tin chi tiết (tên, sđt, địa chỉ). Key liên kết là `userId` (UUID).
3.  **Driver DB (MongoDB):**
    *   `driver_profiles`: Lưu thông tin bằng lái, xe cộ, rating.
    *   `vehicle`: Thông tin xe (Biển số, loại xe).
4.  **Booking DB (MongoDB):**
    *   `bookings`: Lưu thông tin chuyến đi, điểm đón/trả, giá tiền, trạng thái (`CREATED`, `MATCHED`, `COMPLETED`...).

---

## CHƯƠNG 3: GIẢI PHÁP CÔNG NGHỆ VÀ HIỆN THỰC

### 3.1. Cấu trúc tổ chức dự án (Multi-module)
Dự án được tổ chức theo dạng Monorepo, giúp dễ dàng quản lý phiên bản và build toàn bộ hệ thống.

### 3.2. Thư viện dùng chung (Gomirai Common Lib)
Để tuân thủ nguyên tắc **DRY (Don't Repeat Yourself)**, một thư viện `gomirai-common-lib` được xây dựng, bao gồm:
*   **Security:** Cơ chế JWT Filter đồng nhất cho tất cả service.
*   **DTOs:** Các class Request/Response chuẩn (ApiResponse, ErrorResponse).
*   **Events:** Định nghĩa các sự kiện Kafka (UserCreatedEvent, BookingCreatedEvent).
*   **Exceptions:** Custom Business Exception (ResourceNotFound, AccessDenied...).
-> **Lợi ích:** Khi cần sửa logic xác thực JWT, chỉ cần sửa ở Common Lib, tất cả 5-6 services đều được cập nhật.

### 3.3. Giải quyết bài toán Real-time (Centralized Notification Architecture)
Hệ thống yêu cầu khả năng cập nhật trạng thái thời gian thực cho cả Tài xế (nhận chuyến) và Khách hàng (theo dõi xe). Thách thức đặt ra là làm sao để quản lý hàng nghìn kết nối WebSocket mà không gây quá tải cho các Core Services (như BookingService hay DriverService).

**Vấn đề của kiến trúc cũ (Decentralized):**
Nếu để `BookingService` tự quản lý WebSocket, nó vừa phải xử lý logic đặt xe (nặng về CPU/Database), vừa phải duy trì kết nối Statefull với Client. Điều này khiến việc Scale khó khăn và vi phạm nguyên tắc Single Responsibility.

**Giải pháp của GoMirai: Centralized Notification Service**
Hệ thống tách biệt hoàn toàn tầng Giao tiếp Realtime ra khỏi tầng Nghiệp vụ:
*   **Notification Service:** Là service DUY NHẤT chịu trách nhiệm quản lý kết nối WebSocket (`/ws`) với tất cả Client (Driver & Customer). Nó đóng vai trò như một "Gateway" cho các thông báo.
*   **Cơ chế hoạt động:**
    1.  Khi có sự kiện nghiệp vụ (ví dụ: Tìm thấy xe), `BookingService` publish event `BookingMatchedEvent` vào **Kafka**.
    2.  `NotificationService` subscribe topic này.
    3.  `NotificationService` tra cứu `userId` trong event và đẩy thông báo xuống socket của user đó thông qua giao thức **STOMP**.

-> **Lợi ích:**
*   **Decoupling:** Core services (Booking, Driver) hoàn toàn stateless, chỉ việc bắn event và quên đi (Fire-and-forget).
*   **Scalability:** Có thể scale độc lập Notification Service khi số lượng người dùng online tăng cao mà không ảnh hưởng đến khả năng xử lý đơn hàng.

### 3.4. Quản lý giao dịch phân tán (Saga Pattern)
Trong Microservices, không có transaction ACID giữa 2 database khác nhau.
Ví dụ: Khi User đăng ký, cần tạo record ở `AuthDB` VÀ `UserDB`. Nếu `UserDB` lỗi, `AuthDB` phải rollback.

**Giải pháp:** Sử dụng **Choreography Saga Pattern**.
*   B1: AuthService tạo User -> Publish event `UserCreated`.
*   B2: UserService lắng nghe -> Tạo Profile.
*   B3: Nếu UserService lỗi -> Publish event `UserProfileCreationFailed`.
*   B4: AuthService lắng nghe event thất bại -> Xóa User đã tạo (Compensation Action).
-> Đảm bảo tính nhất quán cuối cùng (Eventual Consistency).

---

## CHƯƠNG 4: KẾT QUẢ THỰC NGHIỆM

### 4.1. Môi trường triển khai
Hệ thống được đóng gói hoàn toàn bằng Docker. File `docker-compose.yml` định nghĩa toàn bộ hạ tầng gồm 10+ containers (Services, Databases, Kafka, Zookeeper, Consul, Nginx).
Chỉ cần 1 lệnh duy nhất để khởi chạy: `docker compose up -d`.

### 4.2. Kịch bản chạy thực tế
1.  **Đăng ký tài khoản:**
    *   User nhập SĐT, Pass -> Hệ thống trả về JWT Token ngay lập tức.
    *   Trong nền (background), Profile được tạo bất đồng bộ.
2.  **Quy trình Đặt xe:**
    *   Khách A đặt xe đi từ Quận 1 sang Quận 7.
    *   Hệ thống tính giá: 50.000đ.
    *   Hệ thống quét thấy Tài xế B đang ở gần (cách 500m).
    *   Màn hình Tài xế B hiện Popup đếm ngược 15s.
    *   Tài xế B bấm "Nhận chuyến".
    *   Khách A nhận thông báo "Tài xế B (Xe Accent, 51G-xxx.xx) đang đến".

### 4.3. Đánh giá
*   **Ưu điểm:** Hệ thống hoạt động mượt mà, phân tách rõ ràng trách nhiệm giữa các service. Code clean, dễ đọc, dễ bảo trì.
*   **Khả năng mở rộng:** Có thể dễ dàng tăng số lượng instance của Driver Service lên 5-10 node để chịu tải hàng ngàn tài xế mà không ảnh hưởng đến phần Auth hay Booking.

---

## CHƯƠNG 5: TỔNG KẾT VÀ HƯỚNG PHÁT TRIỂN

### 5.1. Kết luận
Đồ án đã xây dựng thành công bộ khung (framework) vững chắc cho một ứng dụng đặt xe quy mô lớn. Việc áp dụng các kỹ thuật khó như Saga Pattern, Event-Driven và kiến trúc WebSocket lai cho thấy sự nghiêm túc trong đầu tư kỹ thuật.

### 5.2. Hạn chế
*   Chưa tích hợp thanh toán thực tế (VNPAY/Momo/Stripe).
*   Giao diện Frontend (Web/App) mới dừng lại ở mức cơ bản (MVP), chưa tối ưu UX cao cấp.
*   Thuật toán tìm đường và ghép xe (Carpooling) chưa được cài đặt, hiện tại chỉ tìm theo bán kính đơn giản.

### 5.3. Hướng phát triển
1.  **Triển khai Kubernetes (K8s):** Chuyển đổi từ Docker Compose sang K8s để quản lý container chuyên nghiệp hơn, hỗ trợ Auto-scaling.
2.  **Tích hợp AI/Machine Learning:** Dự đoán nhu cầu đặt xe theo khu vực (Heatmap) để điều phối tài xế trước.
3.  **Hoàn thiện Mobile App:** Xây dựng ứng dụng Native trên iOS/Android để tận dụng GPS tốt hơn.

---
**HẾT**

## Hướng dẫn nhanh (Tiếng Việt)

1) Yêu cầu trước khi chạy
- Docker và Docker Compose
- Java 21, Maven (nếu chạy service trực tiếp trên máy)

2) Biến môi trường và bảo mật
- KHÔNG commit secrets. `.gitignore` đã chặn các file `.env`.
- Tạo file `.env` cục bộ (không commit) hoặc truyền biến môi trường trực tiếp khi chạy.

Ví dụ nội dung `.env` (mẫu, tự thay bằng giá trị thật):

```
CONSUL_HOST=localhost
CONSUL_PORT=8500
KAFKA_BOOTSTRAP_SERVERS=localhost:9092

# Tuỳ chọn: lưu URI Mongo tập trung hoặc đặt trực tiếp cho từng service
AUTH_MONGODB_URI=mongodb+srv://<AuthServiceUser>:<password>@<cluster>/?appName=ClusterMaster
USER_MONGODB_URI=mongodb+srv://<UserServiceUser>:<password>@<cluster>/?appName=ClusterMaster
```

3) Cấu hình MongoDB URI (khuyến nghị qua docker-compose)
- File `docker-compose.yml` đã inject biến `SPRING_DATA_MONGODB_URI` cho từng service. Sửa tại:
  - `auth-service.environment.SPRING_DATA_MONGODB_URI`
  - `user-service.environment.SPRING_DATA_MONGODB_URI`

4) Nạp cấu hình route động cho API Gateway qua Consul KV
- Tạo file YAML và đẩy lên Consul KV:

```
cat > /tmp/apigw-gateway-routes.yml <<'YAML'
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://authservice
          predicates:
            - Path=/api/auth/**
        - id: user-service
          uri: lb://userservice
          predicates:
            - Path=/api/users/**
YAML

curl --request PUT \
  --data-binary @/tmp/apigw-gateway-routes.yml \
  http://localhost:8500/v1/kv/config/ApiGateway/application.yml
```

5) Khởi chạy toàn bộ hệ thống
```
docker compose up -d --build
```

6) Kiểm tra nhanh
- Gateway: http://localhost:8080/actuator/health, http://localhost:8080/actuator/gateway
- Consul UI: http://localhost:8500

7) Chạy local (tuỳ chọn)
- Xuất biến môi trường trước khi chạy service:
```
export SPRING_DATA_MONGODB_URI="mongodb+srv://<user>:<password>@<cluster>/?appName=ClusterMaster"
./mvnw spring-boot:run
```


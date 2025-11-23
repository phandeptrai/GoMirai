# 🔧 Common Errors & Troubleshooting Guide

**Version:** 1.0  
**Last Updated:** 2025-11-23  
**Purpose:** Hướng dẫn xử lý các lỗi thường gặp khi thêm service mới vào GoMirai

---

## 📋 Mục Lục

1. [Lỗi JWT Authentication](#1-lỗi-jwt-authentication)
2. [Lỗi Validation](#2-lỗi-validation)
3. [Lỗi Kafka Integration](#3-lỗi-kafka-integration)
4. [Lỗi Docker & Docker Compose](#4-lỗi-docker--docker-compose)
5. [Lỗi Consul Service Discovery](#5-lỗi-consul-service-discovery)
6. [Lỗi API Gateway Routing](#6-lỗi-api-gateway-routing)
7. [Lỗi CORS](#7-lỗi-cors)
8. [Lỗi MongoDB Connection](#8-lỗi-mongodb-connection)
9. [Lỗi Compilation](#9-lỗi-compilation)
10. [Lỗi Authorization](#10-lỗi-authorization)

---

## 1. Lỗi JWT Authentication

### 1.1. Error: "JWT signature does not match locally computed signature"

**Nguyên nhân:**
- JWT_SECRET không đồng nhất giữa các services
- Service mới dùng secret khác với AuthService

**Triệu chứng:**
```json
{
  "error": "Unauthorized",
  "message": "Authentication required"
}
```

**Cách fix:**

```bash
# 1. Kiểm tra JWT_SECRET trong .env
cat .env | grep JWT_SECRET

# 2. Verify JWT_SECRET trong các services
docker-compose exec auth-service env | grep JWT_SECRET
docker-compose exec user-service env | grep JWT_SECRET
docker-compose exec order-service env | grep JWT_SECRET

# 3. Đảm bảo tất cả services dùng CÙNG secret
# File docker-compose.yml:
environment:
  SECURITY_JWT_SECRET: ${JWT_SECRET}  # ✅ Phải giống nhau
```

**Checklist:**
- [ ] JWT_SECRET trong .env phải có prefix `BASE64:` nếu là base64
- [ ] Tất cả services trong docker-compose.yml dùng `${JWT_SECRET}`
- [ ] JwtService.java xử lý key GIỐNG HỆT AuthService
- [ ] Restart tất cả services sau khi thay đổi secret

---

### 1.2. Error: "Invalid authentication principal"

**Nguyên nhân:**
- JWT token không chứa `subject` (userId)
- JWT claims structure không đúng

**Triệu chứng:**
```
SecurityException: Invalid authentication principal
```

**Cách fix:**

```java
// ❌ SAI: JWT không có subject
Jwts.builder()
    .claim("userId", userId.toString())  // Wrong!
    .signWith(key)
    .compact();

// ✅ ĐÚNG: Phải set subject
Jwts.builder()
    .setSubject(userId.toString())  // Correct!
    .claim("role", userRole)
    .signWith(key)
    .compact();

// JwtAuthenticationFilter.java
Claims claims = jwtService.parseToken(token).orElseThrow();
String userIdStr = claims.getSubject();  // ✅ Đọc từ subject
```

---

### 1.3. Error: "Token has expired"

**Nguyên nhân:**
- JWT token hết hạn
- Server time không đồng bộ

**Cách fix:**

```java
// AuthService - JwtService.java
public String generateToken(UUID userId, String role) {
    return Jwts.builder()
        .setSubject(userId.toString())
        .claim("role", role)
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 86400000))  // 24 hours
        .signWith(key)
        .compact();
}

// Kiểm tra system time trong Docker
docker-compose exec auth-service date
docker-compose exec order-service date
# Phải giống nhau!
```

---

## 2. Lỗi Validation

### 2.1. Error: Validation errors không return đúng format

**Nguyên nhân:**
- GlobalExceptionHandler thiếu handler cho `MethodArgumentNotValidException`
- ValidationErrorResponse không được sử dụng

**Triệu chứng:**
```json
// Lỗi: Response dạng này (sai)
{
  "timestamp": "...",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for object='createOrderRequest'..."
}
```

**Cách fix:**

```java
// GlobalExceptionHandler.java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
        MethodArgumentNotValidException ex) {
    
    Map<String, String> fieldErrors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach((error) -> {
        String fieldName = ((FieldError) error).getField();
        String errorMessage = error.getDefaultMessage();
        fieldErrors.put(fieldName, errorMessage);
    });

    ValidationErrorResponse error = new ValidationErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        "Validation Error",
        "Request validation failed. Please check the errors for each field.",
        fieldErrors
    );
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
}
```

**Expected Response:**
```json
{
  "timestamp": "2025-11-23T12:00:00.000",
  "status": 400,
  "error": "Validation Error",
  "message": "Request validation failed. Please check the errors for each field.",
  "errors": {
    "productId": "Product ID is required",
    "quantity": "Quantity must be at least 1"
  }
}
```

---

### 2.2. Error: Validation không chạy

**Nguyên nhân:**
- Thiếu `@Valid` annotation trong Controller
- Thiếu dependency `spring-boot-starter-validation`

**Cách fix:**

```java
// ❌ SAI
@PostMapping
public ResponseEntity<OrderResponse> createOrder(
    @RequestBody CreateOrderRequest request) {  // Thiếu @Valid
    // ...
}

// ✅ ĐÚNG
@PostMapping
public ResponseEntity<OrderResponse> createOrder(
    @Valid @RequestBody CreateOrderRequest request) {  // ✅ Có @Valid
    // ...
}
```

```xml
<!-- pom.xml - Verify dependency -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

---

### 2.3. Error: Validation message không rõ ràng

**Nguyên nhân:**
- Không customize validation messages

**Cách fix:**

```java
// ❌ SAI: Dùng default message
@NotBlank
private String productId;

// ✅ ĐÚNG: Custom message rõ ràng
@NotBlank(message = "Product ID is required")
private String productId;

@NotNull(message = "Quantity is required")
@Min(value = 1, message = "Quantity must be at least 1")
private Integer quantity;

@Email(message = "Invalid email format")
private String email;

@Pattern(regexp = "^[0-9]{9,11}$", message = "Phone number must be 9-11 digits")
private String phoneNumber;
```

---

## 3. Lỗi Kafka Integration

### 3.1. Error: "Deserialization failed - Class not in trusted packages"

**Nguyên nhân:**
- Kafka consumer không whitelist event class package
- Dùng `*` thay vì whitelist cụ thể

**Triệu chứng:**
```
IllegalArgumentException: The class 'com.gomirai.order.events.OrderCreatedEvent' 
is not in the trusted packages
```

**Cách fix:**

```properties
# ❌ SAI: Không secure
spring.kafka.consumer.properties.spring.json.trusted.packages=*

# ✅ ĐÚNG: Whitelist cụ thể
spring.kafka.consumer.properties.spring.json.trusted.packages=com.gomirai.order.events,com.gomirai.payment.events
```

**Best Practice:**
- Chỉ whitelist các package BẠN KIỂM SOÁT
- Mỗi khi thêm event mới, phải update trusted packages
- Không bao giờ dùng `*`

---

### 3.2. Error: Consumer không nhận message

**Nguyên nhân:**
- Topic name không đúng
- GroupId trùng với service khác
- Consumer start trước khi topic được tạo

**Cách fix:**

```bash
# 1. Kiểm tra topic có tồn tại không
docker exec -it kafka bash
kafka-topics --bootstrap-server localhost:9092 --list

# 2. Kiểm tra consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group order-service-group

# 3. Verify configuration
```

```properties
# application.properties
spring.kafka.consumer.group-id=order-service-group  # ✅ Unique per service
spring.kafka.consumer.auto-offset-reset=earliest    # ✅ Read from beginning
```

```java
// Consumer.java
@KafkaListener(
    topics = "${kafka.topic.order-created}",  // ✅ From properties
    groupId = "${spring.kafka.consumer.group-id}"
)
public void handleEvent(OrderCreatedEvent event) {
    log.info("Received event: {}", event);
    // ...
}
```

---

### 3.3. Error: Producer gửi message nhưng không có consumer nhận

**Nguyên nhân:**
- Topic name không khớp giữa producer và consumer
- Serialization/Deserialization config không đúng

**Cách fix:**

```properties
# Producer Service (OrderService)
kafka.topic.order-created=order-created-event  # ✅ Tên topic

# Consumer Service (PaymentService)
kafka.topic.order-created=order-created-event  # ✅ Phải GIỐNG NHAU
```

```bash
# Monitor Kafka topic để debug
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-created-event \
  --from-beginning

# Verify message có đến topic không
```

---

## 4. Lỗi Docker & Docker Compose

### 4.1. Error: "Service cannot connect to MongoDB"

**Nguyên nhân:**
- MongoDB URI sai format
- Service start trước MongoDB ready

**Triệu chứng:**
```
MongoTimeoutException: Timed out after 30000 ms while waiting to connect
```

**Cách fix:**

```yaml
# docker-compose.yml
order-service:
  depends_on:
    mongodb:
      condition: service_healthy  # ✅ Wait for MongoDB ready
  environment:
    SPRING_DATA_MONGODB_URI: ${ORDER_MONGODB_URI}  # ✅ From .env
  restart: on-failure  # ✅ Auto restart if fail

mongodb:
  image: mongo:7
  healthcheck:
    test: echo 'db.runCommand("ping").ok' | mongosh localhost:27017/test --quiet
    interval: 10s
    timeout: 5s
    retries: 5
```

```env
# .env - Verify URI format
ORDER_MONGODB_URI=mongodb://username:password@mongodb:27017/order_db?authSource=admin
```

---

### 4.2. Error: "Port already in use"

**Nguyên nhân:**
- Port đã được service khác sử dụng
- Service cũ vẫn đang chạy

**Cách fix:**

```bash
# 1. Kiểm tra port đang dùng
netstat -an | grep 8083  # Windows
lsof -i :8083            # Linux/Mac

# 2. Stop service đang chạy
docker-compose down

# 3. Đổi port trong application.properties
server.port=8084  # Change port

# 4. Update docker-compose.yml
expose:
  - "8084"  # Internal only - không expose ra ngoài

# ✅ BEST PRACTICE: KHÔNG expose backend ports
# Backend services chỉ access qua Gateway
```

---

### 4.3. Error: "Cannot build image - Maven dependencies fail"

**Nguyên nhân:**
- Network issues trong Docker build
- Maven wrapper không có execute permission

**Cách fix:**

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# ✅ Fix: Give execute permission
RUN chmod +x mvnw

# Download dependencies offline
RUN ./mvnw dependency:go-offline -B

COPY src ./src
RUN ./mvnw clean package -DskipTests
```

```bash
# Local fix
chmod +x mvnw  # Make wrapper executable
git update-index --chmod=+x mvnw  # Git permission

# Build again
docker-compose build order-service
```

---

## 5. Lỗi Consul Service Discovery

### 5.1. Error: "Service không register với Consul"

**Nguyên nhân:**
- Consul configuration sai
- Service không có health endpoint
- Network không kết nối được Consul

**Triệu chứng:**
```bash
curl http://localhost:8500/v1/catalog/services
# OrderService không xuất hiện
```

**Cách fix:**

```properties
# application.properties - Verify configuration
spring.cloud.consul.host=${SPRING_CLOUD_CONSUL_HOST:consul}
spring.cloud.consul.port=${SPRING_CLOUD_CONSUL_PORT:8500}
spring.cloud.consul.discovery.enabled=true         # ✅ Must be true
spring.cloud.consul.discovery.register=true        # ✅ Must be true
spring.cloud.consul.discovery.health-check-path=/actuator/health  # ✅ Must exist
spring.cloud.consul.discovery.health-check-interval=10s
spring.cloud.consul.discovery.instance-id=${spring.application.name}:${server.port}

# ✅ Verify health endpoint accessible
management.endpoints.web.exposure.include=health
```

```bash
# Test health endpoint
docker-compose exec order-service wget -O- http://localhost:8083/actuator/health
# Should return: {"status":"UP"}

# Check Consul UI
open http://localhost:8500
```

---

### 5.2. Error: "Service health check failed"

**Nguyên nhân:**
- Health endpoint return DOWN
- Dependencies (MongoDB, Kafka) không ready

**Cách fix:**

```yaml
# docker-compose.yml - Proper dependencies
order-service:
  depends_on:
    consul:
      condition: service_healthy
    mongodb:
      condition: service_healthy
    kafka:
      condition: service_healthy
```

```properties
# application.properties - Configure health details
management.endpoint.health.show-details=when-authorized
management.endpoint.health.show-components=when-authorized
```

---

## 6. Lỗi API Gateway Routing

### 6.1. Error: "Unknown service" khi call qua Gateway

**Nguyên nhân:**
- Service chưa được add vào Gateway whitelist
- Service name mapping sai

**Triệu chứng:**
```bash
curl http://localhost:8080/api/orders
# Response: {"error": "Unknown service: orders"}
```

**Cách fix:**

```java
// ApiGateway - ProxyController.java

// ✅ ADD service to whitelist
private String mapServiceName(String serviceId) {
    String normalized = serviceId.toLowerCase();
    switch (normalized) {
        case "auth":
            return "AuthService";
        case "user":
        case "users":
            return "UserService";
        case "order":
        case "orders":
            return "OrderService";  // ✅ ADD THIS
        default:
            throw new IllegalArgumentException("Unknown service: " + serviceId);
    }
}

private String getServicePathPrefix(String serviceId) {
    String normalized = serviceId.toLowerCase();
    switch (normalized) {
        case "auth":
            return "/auth";
        case "user":
        case "users":
            return "/api/users";
        case "order":
        case "orders":
            return "/api/orders";  // ✅ ADD THIS
        default:
            return "/" + serviceId;
    }
}
```

**Test:**
```bash
# Frontend call: http://localhost:8080/api/orders
# Gateway routes to: http://order-service:8083/api/orders
```

---

### 6.2. Error: 502 Bad Gateway

**Nguyên nhân:**
- Service không chạy
- Service không registered với Consul
- Network không kết nối

**Cách fix:**

```bash
# 1. Verify service running
docker-compose ps order-service

# 2. Verify service registered
curl http://localhost:8500/v1/catalog/services | jq

# 3. Check Gateway logs
docker-compose logs api-gateway

# 4. Test direct service call (from Gateway container)
docker-compose exec api-gateway curl http://order-service:8083/actuator/health
```

---

## 7. Lỗi CORS

### 7.1. Error: "CORS policy: No 'Access-Control-Allow-Origin' header"

**Nguyên nhân:**
- CORS không được configure trong SecurityConfig
- Frontend origin không được whitelist

**Triệu chứng:**
```
Access to fetch at 'http://localhost:8080/api/orders' from origin 
'http://localhost:3000' has been blocked by CORS policy
```

**Cách fix:**

```java
// SecurityConfig.java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    // ✅ ADD frontend URL
    configuration.setAllowedOrigins(Arrays.asList(
        "http://localhost:3000",      // React
        "http://localhost:4200",      // Angular
        "http://localhost:8080",      // Vue
        "https://yourdomain.com"      // Production
    ));
    
    configuration.setAllowedMethods(Arrays.asList(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
    ));
    
    configuration.setAllowedHeaders(Arrays.asList("*"));
    configuration.setExposedHeaders(Arrays.asList("Authorization"));
    configuration.setAllowCredentials(true);  // ✅ Important!
    configuration.setMaxAge(3600L);
    
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}

// SecurityFilterChain
http.cors(cors -> cors.configurationSource(corsConfigurationSource()));
```

---

### 7.2. Error: "Credentials flag is 'true', but 'Access-Control-Allow-Origin' is '*'"

**Nguyên nhân:**
- CORS config conflict: `allowCredentials(true)` không thể dùng với `allowedOrigins("*")`

**Cách fix:**

```java
// ❌ SAI
configuration.setAllowedOrigins(Arrays.asList("*"));  // Wrong!
configuration.setAllowCredentials(true);

// ✅ ĐÚNG: Specify exact origins
configuration.setAllowedOrigins(Arrays.asList(
    "http://localhost:3000",
    "http://localhost:4200"
));
configuration.setAllowCredentials(true);
```

---

## 8. Lỗi MongoDB Connection

### 8.1. Error: "Authentication failed"

**Nguyên nhân:**
- Username/password sai
- authSource không đúng
- User không có quyền trên database

**Cách fix:**

```env
# .env - Verify URI format
ORDER_MONGODB_URI=mongodb://username:password@mongodb:27017/order_db?authSource=admin
#                          ^^^^^^^^  ^^^^^^^^          ^^^^^^^       ^^^^^^^^^^^^^^
#                          username  password          database      auth database
```

```bash
# Connect to MongoDB container
docker-compose exec mongodb mongosh

# Create user with proper permissions
use admin
db.createUser({
  user: "orderuser",
  pwd: "orderpass",
  roles: [
    { role: "readWrite", db: "order_db" },
    { role: "dbAdmin", db: "order_db" }
  ]
})
```

---

### 8.2. Error: "Database name cannot be null"

**Nguyên nhân:**
- MongoDB URI không chứa database name
- application.properties thiếu database config

**Cách fix:**

```properties
# application.properties
spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}
spring.data.mongodb.database=order_db  # ✅ Explicitly set database name
```

```env
# .env
ORDER_MONGODB_URI=mongodb://username:password@mongodb:27017/order_db
#                                                             ^^^^^^^^ Database name
```

---

## 9. Lỗi Compilation

### 9.1. Error: "Cannot find symbol" - Lombok annotations

**Nguyên nhân:**
- Lombok dependency missing
- IDE không có Lombok plugin
- Annotation processing không enabled

**Cách fix:**

```xml
<!-- pom.xml - Verify dependency -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

```java
// DTO with Lombok
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data                  // ✅ Generates getters, setters, toString, etc.
@NoArgsConstructor     // ✅ Default constructor
@AllArgsConstructor    // ✅ All-args constructor
public class OrderRequest {
    private String productId;
    private Integer quantity;
}
```

**IntelliJ IDEA:**
1. Settings → Plugins → Install "Lombok"
2. Settings → Build → Compiler → Annotation Processors → Enable annotation processing
3. Rebuild project

**VS Code:**
1. Install "Lombok Annotations Support" extension
2. Reload window

---

### 9.2. Error: "XSS Protection deprecated" compilation warning

**Nguyên nhân:**
- Spring Security 6.1+ deprecated XSS Protection header
- Syntax cũ không tương thích

**Cách fix:**

```java
// ❌ SAI (cũ)
http.headers(headers -> headers
    .xssProtection()
    .and()
    .contentSecurityPolicy("default-src 'self'")
);

// ✅ ĐÚNG (Spring Security 6.1+)
http.headers(headers -> headers
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
    .frameOptions(frame -> frame.deny())
    .xssProtection(xss -> {})  // ✅ Empty config - deprecated but harmless
);
```

---

### 9.3. Error: "Cannot access DTO class"

**Nguyên nhân:**
- DTO file rỗng hoặc không có content
- Package import sai

**Cách fix:**

```bash
# Verify file có content
cat src/main/java/com/gomirai/order/dto/OrderRequest.java

# File phải có full class definition
```

```java
// ✅ Complete DTO
package com.gomirai.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    @NotBlank(message = "Product ID is required")
    private String productId;
    
    private Integer quantity;
}
```

---

## 10. Lỗi Authorization

### 10.1. Error: 403 Forbidden - User cannot access own resource

**Nguyên nhân:**
- SecurityUtils.getCurrentUserId() fail
- UUID parsing sai
- Authentication context không có principal

**Cách fix:**

```java
// SecurityUtils.java
public UUID getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new SecurityException("User is not authenticated");
    }
    
    Object principal = authentication.getPrincipal();
    if (principal instanceof UUID) {
        return (UUID) principal;
    }
    
    // ✅ Debug: Log để xem principal type
    log.error("Invalid principal type: {}", principal.getClass().getName());
    throw new SecurityException("Invalid authentication principal");
}
```

```java
// JwtAuthenticationFilter.java - Verify UUID parsing
try {
    UUID userId = UUID.fromString(userIdStr);  // ✅ Parse UUID
    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
        userId,  // ✅ Set userId as principal (not String!)
        null,
        authorities
    );
    SecurityContextHolder.getContext().setAuthentication(auth);
} catch (IllegalArgumentException e) {
    log.warn("Invalid UUID format in token: {}", userIdStr);
}
```

---

### 10.2. Error: @PreAuthorize không hoạt động

**Nguyên nhân:**
- SecurityConfig thiếu `@EnableMethodSecurity`
- Role không có prefix `ROLE_`

**Cách fix:**

```java
// SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // ✅ Enable @PreAuthorize
public class SecurityConfig {
    // ...
}
```

```java
// JwtAuthenticationFilter - Role phải có prefix ROLE_
List<SimpleGrantedAuthority> authorities = role != null 
    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))  // ✅ ROLE_ prefix
    : Collections.emptyList();
```

```java
// Controller
@GetMapping
@PreAuthorize("hasRole('ADMIN')")  // ✅ Check for ROLE_ADMIN
public ResponseEntity<List<Order>> getAllOrders() {
    // Only ADMIN can access
}
```

---

## 🎯 Quick Reference: Error Code Meanings

| HTTP Code | Meaning | Common Cause |
|-----------|---------|--------------|
| 400 | Bad Request | Validation failed, missing required fields |
| 401 | Unauthorized | JWT missing, invalid, or expired |
| 403 | Forbidden | User doesn't have permission (authorization) |
| 404 | Not Found | Resource doesn't exist |
| 409 | Conflict | Duplicate resource (e.g., user already exists) |
| 500 | Internal Server Error | Unhandled exception, service crash |
| 502 | Bad Gateway | Service down, not registered, or unreachable |
| 503 | Service Unavailable | Service temporarily down or overloaded |

---

## 🔍 Debugging Tools

### Logs
```bash
# Service logs
docker-compose logs -f order-service

# All logs
docker-compose logs -f

# Last 100 lines
docker-compose logs --tail=100 order-service
```

### Health Checks
```bash
# Service health
curl http://localhost:8083/actuator/health

# Consul services
curl http://localhost:8500/v1/catalog/services | jq

# Kafka topics
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list
```

### Network Testing
```bash
# Test from Gateway to Service
docker-compose exec api-gateway curl http://order-service:8083/actuator/health

# Test from Service to MongoDB
docker-compose exec order-service wget -O- http://mongodb:27017

# Test from Service to Kafka
docker-compose exec order-service nc -zv kafka 29092
```

---

## 📚 Related Documentation

- [ADDING_NEW_SERVICE.md](./ADDING_NEW_SERVICE.md) - Complete guide to adding a new service
- [README.md](./README.md) - Project overview and setup

---

**🎉 Conclusion**

Guide này cover các lỗi thường gặp nhất khi thêm service mới. Nếu gặp lỗi không có trong list:

1. **Check logs first:** `docker-compose logs -f service-name`
2. **Verify configuration:** Compare với service đang hoạt động
3. **Test step by step:** Isolate problem (JWT, Database, Kafka, etc.)
4. **Ask for help:** Provide logs, error messages, và steps to reproduce

**Remember:** 90% lỗi đến từ configuration, không phải code! Hãy kiểm tra kỹ config trước.


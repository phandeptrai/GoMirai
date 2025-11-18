# Hướng dẫn thêm Service mới vào GoMirai

Tài liệu này hướng dẫn chi tiết cách thêm một microservice mới vào hệ thống GoMirai, bao gồm: xác thực JWT, phát/nhận sự kiện Kafka, và cấu hình Docker.

## 📋 Mục lục

1. [Tạo Service mới](#1-tạo-service-mới)
2. [Cấu hình JWT Authentication](#2-cấu-hình-jwt-authentication)
3. [Phát sự kiện (Kafka Producer)](#3-phát-sự-kiện-kafka-producer)
4. [Lắng nghe sự kiện (Kafka Consumer)](#4-lắng-nghe-sự-kiện-kafka-consumer)
5. [Cấu hình Docker & Docker Compose](#5-cấu-hình-docker--docker-compose)
6. [Đăng ký với Consul](#6-đăng-ký-với-consul)
7. [Cấu hình Gateway](#7-cấu-hình-gateway)
8. [Testing](#8-testing)

---

## 1. Tạo Service mới

### 1.1. Tạo Spring Boot Project

```bash
# Sử dụng Spring Initializr hoặc IDE
# Dependencies cần thiết:
- Spring Web
- Spring Data MongoDB
- Spring Boot Actuator
- Spring Cloud Consul Discovery
- Spring for Apache Kafka
- Spring Security
```

### 1.2. Cấu trúc thư mục

```
NewService/
├── Dockerfile
├── pom.xml
└── src/
    └── main/
        ├── java/com/gomirai/newservice/
        │   ├── NewServiceApplication.java
        │   ├── config/
        │   │   ├── SecurityConfig.java
        │   │   └── KafkaConsumerConfig.java (nếu cần)
        │   ├── controller/
        │   ├── dto/
        │   ├── events/
        │   ├── messaging/
        │   ├── model/
        │   ├── repository/
        │   ├── security/
        │   │   ├── JwtService.java
        │   │   └── JwtAuthenticationFilter.java
        │   └── service/
        └── resources/
            └── application.properties
```

### 1.3. Dependencies trong pom.xml

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- MongoDB -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    
    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.3</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.3</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
    
    <!-- Consul Discovery -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    </dependency>
    
    <!-- Actuator -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 2. Cấu hình JWT Authentication

### 2.1. JwtService.java

```java
package com.gomirai.newservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey secretKey;

    public JwtService(@Value("${security.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

### 2.2. JwtAuthenticationFilter.java

```java
package com.gomirai.newservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<Claims> claims = jwtService.parseToken(token);
            
            if (claims.isPresent()) {
                Claims c = claims.get();
                String subject = c.getSubject();
                String role = c.get("role", String.class);
                
                if (StringUtils.hasText(subject) && StringUtils.hasText(role)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            UUID.fromString(subject), 
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### 2.3. SecurityConfig.java

```java
package com.gomirai.newservice.config;

import com.gomirai.newservice.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

### 2.4. Lấy userId từ Security Context

```java
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.UUID;

public UUID getCurrentUserId() {
    return (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
}
```

---

## 3. Phát sự kiện (Kafka Producer)

### 3.1. Định nghĩa Event

```java
package com.gomirai.newservice.events;

import java.util.UUID;

public record OrderCreatedEvent(
    UUID orderId,
    UUID userId,
    String status,
    Double totalAmount
) {}
```

### 3.2. Event Producer

```java
package com.gomirai.newservice.messaging;

import com.gomirai.newservice.events.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String orderCreatedTopic;

    public OrderEventsProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topic.order-created}") String orderCreatedTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    public void sendOrderCreated(OrderCreatedEvent event) {
        kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), event);
    }
}
```

### 3.3. Sử dụng trong Service

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderEventsProducer eventsProducer;
    
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // Lưu vào database
        Order order = orderRepository.save(newOrder);
        
        // Phát sự kiện
        eventsProducer.sendOrderCreated(
            new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getStatus(),
                order.getTotalAmount()
            )
        );
        
        return mapToResponse(order);
    }
}
```

---

## 4. Lắng nghe sự kiện (Kafka Consumer)

### 4.1. KafkaConsumerConfig.java

```java
package com.gomirai.newservice.config;

import com.gomirai.newservice.events.OrderCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        
        JsonDeserializer<OrderCreatedEvent> jsonDeserializer = 
            new JsonDeserializer<>(OrderCreatedEvent.class, false);
        jsonDeserializer.setUseTypeHeaders(false);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setRemoveTypeHeaders(true);
        
        return new DefaultKafkaConsumerFactory<>(
            config, 
            new StringDeserializer(), 
            jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

### 4.2. Event Consumer

```java
package com.gomirai.newservice.consumer;

import com.gomirai.newservice.events.OrderCreatedEvent;
import com.gomirai.newservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "${kafka.topic.order-created}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent: orderId={}, userId={}", 
            event.orderId(), event.userId());
        
        try {
            notificationService.sendOrderConfirmation(event);
            log.info("Successfully processed order notification for orderId: {}", 
                event.orderId());
        } catch (Exception e) {
            log.error("Failed to process order notification for orderId: {}", 
                event.orderId(), e);
            throw e; // Để Kafka retry
        }
    }
}
```

---

## 5. Cấu hình Docker & Docker Compose

### 5.1. Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline

COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 5.2. Thêm vào docker-compose.yml

```yaml
services:
  # ... existing services ...

  # New Service
  new-service:
    build:
      context: ./NewService
      dockerfile: Dockerfile
    container_name: new-service
    ports:
      - "8083:8083"
    depends_on:
      consul:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_DATA_MONGODB_URI: ${NEW_SERVICE_MONGODB_URI}
      SECURITY_JWT_SECRET: ${JWT_SECRET}
      SPRING_CLOUD_CONSUL_HOST: consul
      SPRING_CLOUD_CONSUL_PORT: 8500
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    networks:
      - gomirai-network
    restart: on-failure
```

### 5.3. File .env (Root project)

```env
# Auth Service MongoDB
AUTH_MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/auth_db

# User Service MongoDB
USER_MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/user_db

# New Service MongoDB
NEW_SERVICE_MONGODB_URI=mongodb+srv://user:password@cluster.mongodb.net/new_service_db

# JWT Secret (MUST be same across all services)
JWT_SECRET=your-super-secret-jwt-key-minimum-256-bits-long-please-change-this-in-production
```

---

## 6. Đăng ký với Consul

### 6.1. application.properties

```properties
spring.application.name=NewService
server.port=8083

# MongoDB Configuration
spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}
spring.data.mongodb.database=new_service_db

# Consul Configuration
spring.cloud.consul.host=${SPRING_CLOUD_CONSUL_HOST:consul}
spring.cloud.consul.port=${SPRING_CLOUD_CONSUL_PORT:8500}
spring.cloud.consul.discovery.enabled=true
spring.cloud.consul.discovery.register=true
spring.cloud.consul.discovery.health-check-path=/actuator/health
spring.cloud.consul.discovery.health-check-interval=10s
spring.cloud.consul.discovery.instance-id=${spring.application.name}:${server.port}

# Kafka Configuration
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:kafka:29092}
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.group-id=new-service-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=*
spring.kafka.consumer.auto-offset-reset=earliest

# Kafka Topics
kafka.topic.order-created=order-created-event
kafka.topic.order-updated=order-updated-event

# JWT
security.jwt.secret=${SECURITY_JWT_SECRET}

# Actuator
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=always
```

---

## 7. Cấu hình Gateway

### 7.1. Thêm mapping vào ProxyController

```java
// Trong ApiGateway/src/main/java/com/gomirai/gateway/controller/ProxyController.java

private String mapServiceName(String serviceId) {
    if (serviceId == null || serviceId.isEmpty()) {
        return serviceId;
    }
    String normalized = serviceId.toLowerCase();
    switch (normalized) {
        case "auth":
            return "AuthService";
        case "user":
        case "users":
            return "UserService";
        case "order":
        case "orders":
            return "NewService";  // THÊM DÒNG NÀY
        default:
            if (serviceId.length() > 0) {
                return serviceId.substring(0, 1).toUpperCase() + 
                       (serviceId.length() > 1 ? serviceId.substring(1).toLowerCase() : "") + 
                       "Service";
            }
            return serviceId;
    }
}

private String getServicePathPrefix(String serviceId) {
    if (serviceId == null || serviceId.isEmpty()) {
        return "";
    }
    String normalized = serviceId.toLowerCase();
    switch (normalized) {
        case "auth":
            return "/auth";
        case "user":
        case "users":
            return "/api/users";
        case "order":
        case "orders":
            return "/api/orders";  // THÊM DÒNG NÀY
        default:
            return "/" + serviceId;
    }
}
```

---

## 8. Testing

### 8.1. Build và Run

```bash
# Thêm biến môi trường vào .env trước
# NEW_SERVICE_MONGODB_URI=...

# Build service mới
docker-compose build new-service

# Start tất cả services
docker-compose up -d

# Xem logs
docker-compose logs -f new-service

# Kiểm tra service đã đăng ký với Consul
curl http://localhost:8500/v1/catalog/services
```

### 8.2. Test endpoints

```bash
# 1. Get JWT token từ AuthService
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"0123456789","password":"password123"}'

# Response: {"userId":"...","role":"CUSTOMER","token":"eyJhbGc..."}

# 2. Call new service qua Gateway
curl -X GET http://localhost:8080/api/orders \
  -H "Authorization: Bearer eyJhbGc..."

# 3. Kiểm tra health
curl http://localhost:8080/health/newservice
```

### 8.3. Test Kafka Events

```bash
# Vào Kafka container
docker exec -it kafka bash

# Xem danh sách topics
kafka-topics --bootstrap-server localhost:9092 --list

# Theo dõi messages trong topic
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-created-event \
  --from-beginning

# Kiểm tra consumer groups
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group new-service-group
```

---

## 9. Checklist khi thêm Service mới

- [ ] Tạo Spring Boot project với dependencies đầy đủ
- [ ] Implement JWT Authentication (JwtService, JwtAuthenticationFilter, SecurityConfig)
- [ ] Cấu hình MongoDB connection
- [ ] Cấu hình Kafka Producer (nếu service phát events)
- [ ] Cấu hình Kafka Consumer (nếu service lắng nghe events)
- [ ] Tạo Dockerfile theo chuẩn
- [ ] Thêm service vào docker-compose.yml
- [ ] Thêm biến môi trường vào .env
- [ ] Cấu hình Consul discovery
- [ ] Cấu hình Gateway routing
- [ ] Test JWT authentication
- [ ] Test Kafka events
- [ ] Kiểm tra logs và health check

---

## 10. Best Practices

### 10.1. Security
- ✅ Luôn validate JWT token ở mọi endpoint (trừ public endpoints)
- ✅ Sử dụng SAME JWT_SECRET cho tất cả services
- ✅ Set SessionCreationPolicy.STATELESS
- ✅ Không log sensitive data (tokens, passwords)

### 10.2. Kafka
- ✅ Luôn set `auto-offset-reset=earliest` để không mất message khi service restart
- ✅ Sử dụng `groupId` unique cho mỗi service
- ✅ Throw exception trong consumer để Kafka retry khi xử lý lỗi
- ✅ Log chi tiết các events nhận được
- ✅ Sử dụng Record classes cho events (immutable)

### 10.3. Docker
- ✅ Sử dụng multi-stage build để giảm image size
- ✅ Sử dụng healthcheck cho dependencies
- ✅ Set restart policy = on-failure
- ✅ Đặt tên container rõ ràng
- ✅ Sử dụng environment variables thay vì hardcode

### 10.4. Monitoring
- ✅ Enable Spring Actuator endpoints
- ✅ Log đầy đủ (INFO cho business logic, ERROR cho exceptions)
- ✅ Track Kafka consumer lag
- ✅ Monitor service health qua Consul

---

## 11. Troubleshooting

### Service không connect được Kafka
```bash
# Kiểm tra Kafka đang chạy
docker-compose ps kafka

# Kiểm tra logs
docker-compose logs kafka

# Fix: Đảm bảo SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092 (không phải localhost)
```

### Service không đăng ký với Consul
```bash
# Kiểm tra Consul
curl http://localhost:8500/v1/catalog/services

# Fix: Đảm bảo spring.cloud.consul.discovery.enabled=true
```

### JWT Authentication fail
```bash
# Đảm bảo JWT_SECRET giống nhau ở tất cả services
# Check trong .env file
```

### Kafka Consumer không nhận message
```bash
# Kiểm tra consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group new-service-group

# Check lag và offset
```

---

## 12. Example: Payment Service

Xem ví dụ đầy đủ về implementation trong các services hiện có:
- **AuthService**: Producer only (phát UserRegisteredEvent)
- **UserService**: Consumer only (lắng nghe UserRegisteredEvent)

Để tạo service vừa là Producer vừa Consumer (như PaymentService), kết hợp cả 2 patterns trên.

---

**Lưu ý**: Tài liệu này được viết dựa trên kiến trúc hiện tại của GoMirai. Khi thêm service mới, hãy đảm bảo tuân thủ đúng các patterns đã thiết lập để maintain consistency.


# 🚀 Configuration Guide: Adding a New Service

**Mục đích:** Tài liệu này cung cấp các **mẫu cấu hình chuẩn (Configuration Templates)** cần thiết để thêm một microservice mới vào hệ thống GoMirai.

---

## 1. Maven Configuration (`pom.xml`)

Bắt buộc phải có `gomirai-common-lib` và các dependencies cho Cloud/Kafka.

```xml
<dependencies>
    <!-- 1. Common Library (Core) -->
    <dependency>
        <groupId>com.gomirai</groupId>
        <artifactId>gomirai-common-lib</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- 2. Service Discovery (Consul) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-consul-discovery</artifactId>
    </dependency>

    <!-- 3. Messaging (Kafka) -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- 4. Database (MongoDB) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <!-- 5. Web & Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- 6. Monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 2. Application Properties (`application.properties`)

Copy và sửa đổi các giá trị tương ứng.

```properties
# ==========================================
# 1. Service Identity
# ==========================================
spring.application.name=YourServiceName
server.port=808x  # Chọn port chưa sử dụng (8083, 8084...)

# ==========================================
# 2. Consul Discovery Configuration
# ==========================================
# Default: localhost cho dev, override bằng 'consul' trong Docker
spring.cloud.consul.host=${SPRING_CLOUD_CONSUL_HOST:localhost}
spring.cloud.consul.port=${SPRING_CLOUD_CONSUL_PORT:8500}
spring.cloud.consul.discovery.enabled=true
spring.cloud.consul.discovery.register=true
spring.cloud.consul.discovery.health-check-path=/actuator/health
spring.cloud.consul.discovery.health-check-interval=10s
spring.cloud.consul.discovery.instance-id=${spring.application.name}:${server.port}

# ==========================================
# 3. Kafka Configuration
# ==========================================
spring.kafka.bootstrap-servers=${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}

# Producer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer

# Consumer
spring.kafka.consumer.group-id=your-service-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.auto-offset-reset=earliest

# ✅ SECURITY: Trusted Packages (QUAN TRỌNG)
# Phải bao gồm package chứa Event của bạn VÀ common-lib
spring.kafka.consumer.properties.spring.json.trusted.packages=com.gomirai.yourservice.event,com.gomirai.common.dto.event

# Optional: Disable type headers if sharing events across services with different package names
spring.kafka.consumer.properties.spring.json.use.type.headers=false
spring.kafka.consumer.properties.spring.json.remove.type.headers=true

# ==========================================
# 4. Database Configuration
# ==========================================
spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}
spring.data.mongodb.database=your_service_db

# ==========================================
# 5. Security & JWT (from Common Lib)
# ==========================================
security.jwt.secret=${SECURITY_JWT_SECRET}
security.jwt.access-ttl-ms=3600000

# ==========================================
# 6. Actuator (Health Check)
# ==========================================
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=when-authorized

# ==========================================
# 7. CORS Configuration
# ==========================================
cors.allowed.origins=http://localhost:8080
cors.allowed.methods=GET,POST,PUT,DELETE,OPTIONS
cors.allowed.headers=*
cors.exposed.headers=Authorization
cors.allow.credentials=true
cors.max.age=3600
```

---

## 3. Docker Configuration (`Dockerfile`)

Vì `gomirai-common-lib` là thư viện nội bộ, chúng ta cần build nó trước khi build service chính (Monorepo pattern).

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. Copy & Build Common Lib
COPY gomirai-common-lib ./gomirai-common-lib
WORKDIR /workspace/gomirai-common-lib
RUN apk add --no-cache maven && mvn clean install -DskipTests

# 2. Build Your Service
WORKDIR /workspace/YourService
COPY YourService/pom.xml .
COPY YourService/mvnw .
COPY YourService/.mvn ./.mvn
RUN ./mvnw dependency:go-offline -B

# 3. Copy Source & Package
COPY YourService/src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy JAR từ Stage 1
COPY --from=build /workspace/YourService/target/*.jar app.jar

# Expose port (Internal only)
EXPOSE 808x

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Lưu ý:** Khi build docker image, bạn phải chạy lệnh build từ **thư mục gốc** của dự án (GoMirai) để Docker có thể access được `gomirai-common-lib`.

```bash
# Tại thư mục GoMirai/
docker build -f YourService/Dockerfile -t your-service .
```

---

## 4. Docker Compose (`docker-compose.yml`)

Thêm service mới vào file `docker-compose.yml` gốc.

```yaml
  your-service:
    build:
      context: .  # Build từ root context để access common-lib nếu cần
      dockerfile: YourService/Dockerfile
    container_name: your-service
    
    # ✅ SECURITY: KHÔNG expose port ra host (trừ khi debug)
    # ports:
    #   - "808x:808x"
    expose:
      - "808x"  # Chỉ expose cho các container khác trong mạng
      
    depends_on:
      consul:
        condition: service_healthy
      kafka:
        condition: service_healthy
        
    environment:
      # Database
      SPRING_DATA_MONGODB_URI: ${YOUR_SERVICE_MONGODB_URI}
      
      # Security
      SECURITY_JWT_SECRET: ${JWT_SECRET}
      
      # Infrastructure
      SPRING_CLOUD_CONSUL_HOST: consul
      SPRING_CLOUD_CONSUL_PORT: 8500
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
      
    networks:
      - gomirai-network
    restart: on-failure
```

---

## 5. Saga & Event Configuration

### 5.1. Định nghĩa Topic (trong `application.properties`)

```properties
kafka.topic.your-entity-created=your-entity-created-event
kafka.topic.saga-compensation=saga-compensation-event
```

### 5.2. Java Config (`KafkaProducerConfig.java`)

Không cần tạo lại nếu dùng `KafkaAutoConfiguration` của Spring Boot, nhưng nếu cần custom:

```java
@Configuration
public class KafkaProducerConfig {
    // Spring Boot tự động config KafkaTemplate dựa trên properties
    // Chỉ cần inject KafkaTemplate<String, Object> là dùng được
}
```

### 5.3. Main Class (`Application.java`)

**QUAN TRỌNG:** Phải scan package `com.gomirai.common` để load các bean Security/Exception.

```java
@SpringBootApplication
@EnableDiscoveryClient
@ComponentScan(basePackages = {
    "com.gomirai.yourservice", // Package của service này
    "com.gomirai.common"       // ✅ BẮT BUỘC: Common Lib
})
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

---

## 6. JWT Security Configuration (Using Common Lib)

Common Lib cung cấp sẵn `JwtAuthenticationFilter` và `JwtAuthenticationEntryPoint`. Bạn chỉ cần cấu hình `SecurityConfig` để sử dụng chúng.

### 6.1. SecurityConfig Template

```java
package com.gomirai.yourservice.config;

import com.gomirai.common.security.JwtAuthenticationEntryPoint;
import com.gomirai.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;

    @Value("${cors.allowed.origins}")
    private String allowedOrigins;

    @Value("${cors.allowed.methods}")
    private String allowedMethods;

    @Value("${cors.allowed.headers}")
    private String allowedHeaders;

    @Value("${cors.exposed.headers}")
    private String exposedHeaders;

    @Value("${cors.allow.credentials}")
    private boolean allowCredentials;

    @Value("${cors.max.age}")
    private long maxAge;

    // ✅ Autowire từ Common Lib (nhờ @ComponentScan)
    public SecurityConfig(JwtAuthenticationFilter jwtFilter, JwtAuthenticationEntryPoint authEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // ✅ CORS Config
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll() // Public health check
                .anyRequest().authenticated()                    // Tất cả request khác cần JWT
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Read from application.properties
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(Arrays.asList("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }
        
        configuration.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

## 7. API Gateway Configuration

Sau khi thêm service mới, bạn cần cấu hình API Gateway để route request tới service đó.

### 7.1. Cập nhật `ApiGateway/src/main/resources/application.properties`

Thêm route ID và URI cho service mới.

```properties
# ... existing routes ...

# Route cho YourService
spring.cloud.gateway.routes[n].id=your-service
spring.cloud.gateway.routes[n].uri=lb://YourServiceName  # Tên đăng ký với Consul
spring.cloud.gateway.routes[n].predicates[0]=Path=/api/your-resource/**
```

*Lưu ý: Nếu dùng Consul KV để lưu config (như trong README), bạn cần update KV store thay vì file properties.*

### 7.2. Whitelist Service (Nếu Gateway có ProxyController)

Nếu Gateway có logic custom để proxy request (ví dụ `ProxyController.java`), hãy thêm service mới vào whitelist.

```java
// ApiGateway/.../ProxyController.java

private String mapServiceName(String serviceId) {
    switch (serviceId.toLowerCase()) {
        case "auth": return "AuthService";
        case "user": return "UserService";
        case "yourservice": return "YourServiceName"; // ✅ Thêm dòng này
        default: throw new IllegalArgumentException("Unknown service");
    }
}
```

---

## 8. Common Lib Usage Guide

### 8.1. Lấy thông tin User hiện tại (`SecurityUtils`)

```java
import com.gomirai.common.security.SecurityUtils;

@Service
public class YourService {
    private final SecurityUtils securityUtils; // Autowire

    public void doSomething() {
        UUID userId = securityUtils.getCurrentUserId();
        String role = securityUtils.getCurrentUserRole();
        
        // Validate quyền sở hữu
        securityUtils.validateOwnershipOrAdmin(resourceOwnerId);
    }
}
```

### 8.2. Xử lý Exception (`GlobalExceptionHandler`)

Không cần làm gì cả! `GlobalExceptionHandler` trong Common Lib sẽ tự động bắt các exception sau và trả về JSON chuẩn:
- `BusinessException` -> 400 Bad Request
- `NotFoundException` -> 404 Not Found
- `ForbiddenException` -> 403 Forbidden
- `UnauthorizedException` -> 401 Unauthorized

### 8.3. Tạo Event Kafka (`BaseEvent`)

```java
import com.gomirai.common.dto.event.BaseEvent;

public class YourEntityCreatedEvent extends BaseEvent {
    private String someData;
    
    public YourEntityCreatedEvent(String someData) {
        super(); // Tự động set id, timestamp
        this.someData = someData;
    }
}
```

---

## 9. Checklist Kiểm Tra Cuối Cùng

- [ ] **Dependencies**: Đã có `gomirai-common-lib` trong pom.xml?
- [ ] **ComponentScan**: Đã scan `com.gomirai.common` trong Main class?
- [ ] **Consul**: Service có hiện lên trong Consul UI (`localhost:8500`) không?
- [ ] **Kafka**: `trusted.packages` đã bao gồm package event chưa?
- [ ] **Docker Network**: Service có nằm trong `gomirai-network` không?
- [ ] **Security**: Đã set `JWT_SECRET` trong environment variables chưa?
- [ ] **Gateway**: Đã thêm route trong API Gateway chưa?

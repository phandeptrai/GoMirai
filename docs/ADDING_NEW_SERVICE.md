# 🚀 Hướng Dẫn Thêm Service Mới Vào GoMirai (With Common Library)

**Version:** 3.0  
**Last Updated:** 2025-11-24  
**Difficulty:** Intermediate  

Tài liệu này hướng dẫn **CHI TIẾT** cách thêm một microservice mới vào hệ thống GoMirai sử dụng **gomirai-common-lib** để tránh code duplication và đảm bảo consistency.

---

## 📋 Mục Lục

1. [Prerequisites](#1-prerequisites)
2. [Understanding Common Library](#2-understanding-common-library)
3. [Tạo Service Mới](#3-tạo-service-mới)
4. [Configure Security (Using Common Lib)](#4-configure-security-using-common-lib)
5. [Validation & Error Handling (Using Common Lib)](#5-validation--error-handling-using-common-lib)
6. [Kafka Integration (Using Common Lib)](#6-kafka-integration-using-common-lib)
7. [Docker Configuration](#7-docker-configuration)
8. [Gateway Configuration](#8-gateway-configuration)
9. [Testing & Verification](#9-testing--verification)
10. [Checklist](#10-checklist)
11. [Troubleshooting](#11-troubleshooting)

---

## 1. Prerequisites

### 1.1. Required Knowledge
- ✅ Spring Boot 3.5+
- ✅ Spring Security 6+
- ✅ JWT Authentication
- ✅ Apache Kafka
- ✅ Docker & Docker Compose
- ✅ MongoDB
- ✅ **Maven dependency management**

### 1.2. Required Tools
- ✅ JDK 21
- ✅ Maven 3.9+
- ✅ Docker Desktop
- ✅ IntelliJ IDEA / VS Code
- ✅ Postman / cURL

### 1.3. Environment Setup
```bash
# Clone project
git clone <repo-url>
cd GoMirai

# Build common library FIRST!
cd gomirai-common-lib
mvn clean install
cd ..

# Verify .env file exists
ls -la .env

# Verify JWT_SECRET is set
grep JWT_SECRET .env
```

---

## 2. Understanding Common Library

### 2.1. What is gomirai-common-lib?

**gomirai-common-lib** là một Maven library chứa shared components được sử dụng bởi TẤT CẢ microservices trong GoMirai.

### 2.2. Components Provided by Common Library

#### ✅ **Security** (`com.gomirai.common.security`)
- `JwtService` - JWT token generation & validation
- `JwtAuthenticationFilter` - Spring Security filter
- `JwtAuthenticationEntryPoint` - 401 error handler
- `SecurityUtils` - Authorization helpers

#### ✅ **DTOs** (`com.gomirai.common.dto`)
- `response/ErrorResponse` - Standard error response
- `response/ValidationErrorResponse` - Validation errors
- `response/ApiResponse<T>` - Generic API response
- `event/BaseEvent` - Base for all Kafka events
- `event/UserRegisteredEvent` - User registration event

#### ✅ **Enums** (`com.gomirai.common.enums`)
- `Role` - User roles (CUSTOMER, DRIVER, ADMIN)
- `AuthProvider` - Auth providers (LOCAL, GOOGLE, etc.)
- `ServiceName` - Service names

#### ✅ **Exceptions** (`com.gomirai.common.exception`)
- `BusinessException`, `UnauthorizedException`, `ForbiddenException`, `NotFoundException`
- `GlobalExceptionHandler` - Centralized exception handling

#### ✅ **Utils** (`com.gomirai.common.util`)
- `ValidationUtil` - Input validation (phone, email, password)
- `DateTimeUtil` - Date/time helpers
- `StringUtil` - String manipulation

#### ✅ **Constants** (`com.gomirai.common.constant`)
- `ValidationConstants` - Validation rules & messages
- `SystemConstants` - System-wide constants

### 2.3. Benefits

- 🎯 **No Code Duplication** - Write once, use everywhere
- 🔒 **Consistent Security** - Same JWT implementation
- 📝 **Standardized Responses** - Consistent error formats
- 🚀 **Faster Development** - No need to rewrite common code
- 🛠️ **Easy Maintenance** - Update once, affects all services

### 2.4. What You Should NOT Create

**❌ DON'T create these in your new service:**
- ❌ `JwtService.java`
- ❌ `JwtAuthenticationFilter.java`
- ❌ `JwtAuthenticationEntryPoint.java`
- ❌ `SecurityUtils.java`
- ❌ `ErrorResponse.java`
- ❌ `ValidationErrorResponse.java`
- ❌ `GlobalExceptionHandler.java`
- ❌ `Role.java` enum
- ❌ Kafka `BaseEvent.java`

**✅ Instead, use them from common-lib!**

---

## 3. Tạo Service Mới

### 3.1. Example: OrderService

Let's create an OrderService as example.

### 3.2. Spring Initializr Configuration

**Project Settings:**
- **Project:** Maven
- **Language:** Java
- **Spring Boot:** 3.5.7
- **Java:** 21
- **Packaging:** Jar
- **Group:** com.gomirai
- **Artifact:** order-service
- **Name:** OrderService
- **Package name:** com.gomirai.order

**Dependencies:**
```xml
- Spring Web
- Spring Data MongoDB
- Spring Boot Actuator
- Spring Security
- Spring for Apache Kafka
- Spring Cloud Consul Discovery
- Validation
- Lombok
```

### 3.3. Cấu Trúc Thư Mục

```
OrderService/
├── Dockerfile
├── pom.xml                    # Will include gomirai-common-lib
├── mvnw
├── mvnw.cmd
├── .mvn/
└── src/
    ├── main/
    │   ├── java/com/gomirai/order/
    │   │   ├── OrderServiceApplication.java      # @ComponentScan for common
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java           # Uses common-lib components
    │   │   ├── controller/
    │   │   │   └── OrderController.java
    │   │   ├── dto/
    │   │   │   ├── CreateOrderRequest.java       # Service-specific only
    │   │   │   ├── UpdateOrderRequest.java
    │   │   │   └── OrderResponse.java
    │   │   │   # ❌ NO ErrorResponse (use common-lib)
    │   │   │   # ❌ NO ValidationErrorResponse (use common-lib)
    │   │   ├── events/
    │   │   │   └── OrderCreatedEvent.java        # Extends common-lib BaseEvent
    │   │   ├── messaging/
    │   │   │   ├── OrderEventsProducer.java
    │   │   │   └── PaymentEventConsumer.java
    │   │   ├── model/
    │   │   │   ├── Order.java
    │   │   │   └── OrderStatus.java
    │   │   ├── repository/
    │   │   │   └── OrderRepository.java
    │   │   │   # ❌ NO security/ folder (use common-lib)
    │   │   │   # ❌ NO exception/GlobalExceptionHandler (use common-lib)
    │   │   └── service/
    │   │       └── OrderService.java             # Uses common-lib SecurityUtils
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/gomirai/order/
            └── OrderServiceApplicationTests.java
```

### 3.4. pom.xml

**⭐ IMPORTANT: Add gomirai-common-lib dependency**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>
    
    <groupId>com.gomirai</groupId>
    <artifactId>OrderService</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>OrderService</name>
    <description>Order Management Service</description>
    
    <properties>
        <java.version>21</java.version>
        <spring-cloud.version>2025.0.0</spring-cloud.version>
    </properties>
    
    <dependencies>
        <!-- ⭐ GOMIRAI COMMON LIBRARY - ADD THIS FIRST! -->
        <dependency>
            <groupId>com.gomirai</groupId>
            <artifactId>gomirai-common-lib</artifactId>
            <version>1.0.0</version>
        </dependency>
        
        <!-- Web -->
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
        
        <!-- ❌ NO NEED for JJWT dependencies (already in common-lib) -->
        
        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
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
        
        <!-- DevTools (optional) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        
        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.kafka</groupId>
            <artifactId>spring-kafka-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 4. Configure Security (Using Common Lib)

### 4.1. Main Application Class

**⭐ CRITICAL: Add @ComponentScan to include common-lib packages**

```java
package com.gomirai.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka  // If using Kafka
@ComponentScan(basePackages = {
    "com.gomirai.order",    // Your service package
    "com.gomirai.common"    // ⭐ Common library package - MUST INCLUDE!
})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

**Why @ComponentScan?**
- Spring needs to scan common-lib packages to register beans
- Without this, JwtService, SecurityUtils, GlobalExceptionHandler won't be available
- This enables autowiring of common-lib components

### 4.2. SecurityConfig.java

**✅ Use common-lib components - NO custom security classes!**

```java
package com.gomirai.order.config;

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

import com.gomirai.common.security.JwtAuthenticationEntryPoint;  // ✅ From common-lib
import com.gomirai.common.security.JwtAuthenticationFilter;      // ✅ From common-lib

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;                // ✅ Autowired from common-lib
    private final JwtAuthenticationEntryPoint authEntryPoint;       // ✅ Autowired from common-lib

    public SecurityConfig(
            JwtAuthenticationFilter jwtFilter,
            JwtAuthenticationEntryPoint authEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disabled for REST API with JWT (stateless)
            .csrf(csrf -> csrf.disable())
            
            // Stateless session
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // CORS configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            
            // Exception handling
            .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
            
            // Authorization - ALL endpoints require authentication
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()  // Only health endpoint
                .anyRequest().authenticated()  // Everything else needs JWT
            )
            
            // JWT filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // ✅ Security headers
        http.headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
            .frameOptions(frame -> frame.deny())
        );
        
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",
            "http://localhost:4200",
            "http://localhost:8080"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

**📝 Notes:**
- ❌ No need to create `JwtService`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`
- ✅ Just autowire them from common-lib
- ✅ Spring finds them because of `@ComponentScan`

---

## 5. Validation & Error Handling (Using Common Lib)

### 5.1. Use Common-Lib Error Responses

**❌ DON'T create these:**
```java
// ❌ Don't create ErrorResponse.java
// ❌ Don't create ValidationErrorResponse.java
// ❌ Don't create GlobalExceptionHandler.java
```

**✅ Instead, use from common-lib:**
```java
import com.gomirai.common.dto.response.ErrorResponse;
import com.gomirai.common.dto.response.ValidationErrorResponse;
import com.gomirai.common.dto.response.ApiResponse;

// GlobalExceptionHandler is automatically registered via @ComponentScan!
```

### 5.2. Service-Specific DTOs with Validation

**Create your service-specific DTOs:**

```java
package com.gomirai.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    
    @NotBlank(message = "Product ID is required")
    private String productId;
    
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
    
    private String notes;  // Optional field
}
```

### 5.3. Controller with @Valid

**Validation errors are automatically handled by common-lib GlobalExceptionHandler!**

```java
package com.gomirai.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gomirai.common.dto.response.ApiResponse;  // ✅ From common-lib
import com.gomirai.common.security.SecurityUtils;     // ✅ From common-lib
import com.gomirai.order.dto.CreateOrderRequest;
import com.gomirai.order.dto.OrderResponse;
import com.gomirai.order.service.OrderService;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderService orderService;
    private final SecurityUtils securityUtils;  // ✅ Autowired from common-lib

    public OrderController(OrderService orderService, SecurityUtils securityUtils) {
        this.orderService = orderService;
        this.securityUtils = securityUtils;
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // ✅ @Valid triggers validation
        // ✅ If validation fails, common-lib GlobalExceptionHandler handles it
        // ✅ Returns ValidationErrorResponse automatically
        
        UUID currentUserId = securityUtils.getCurrentUserId();  // ✅ From common-lib
        OrderResponse order = orderService.createOrder(currentUserId, request);
        return ResponseEntity.ok(ApiResponse.success(order));  // ✅ From common-lib
    }
    
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID orderId) {
        // ✅ Authorization check using common-lib SecurityUtils
        OrderResponse order = orderService.getOrder(orderId);
        securityUtils.validateOwnershipOrAdmin(order.getUserId());  // ✅ From common-lib
        
        return ResponseEntity.ok(ApiResponse.success(order));
    }
}
```

### 5.4. Service Layer with Common-Lib Exceptions

```java
package com.gomirai.order.service;

import org.springframework.stereotype.Service;

import com.gomirai.common.exception.NotFoundException;      // ✅ From common-lib
import com.gomirai.common.exception.ForbiddenException;     // ✅ From common-lib
import com.gomirai.common.security.SecurityUtils;           // ✅ From common-lib
import com.gomirai.order.dto.CreateOrderRequest;
import com.gomirai.order.dto.OrderResponse;
import com.gomirai.order.model.Order;
import com.gomirai.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final SecurityUtils securityUtils;  // ✅ Autowired from common-lib

    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        Order order = new Order();
        order.setUserId(userId);
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        
        Order saved = orderRepository.save(order);
        return OrderResponse.from(saved);
    }

    public OrderResponse getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new NotFoundException("Order not found: " + orderId));  // ✅ From common-lib
        
        // ✅ Common-lib GlobalExceptionHandler converts NotFoundException to 404 response
        
        return OrderResponse.from(order);
    }
}
```

**📝 Notes:**
- ✅ Throw common-lib exceptions (`NotFoundException`, `ForbiddenException`, etc.)
- ✅ Common-lib `GlobalExceptionHandler` automatically converts them to proper HTTP responses
- ✅ No need to create custom exception handlers

---

## 6. Kafka Integration (Using Common Lib)

### 6.1. Event Definition (Extend BaseEvent)

**✅ Extend common-lib BaseEvent for consistency:**

```java
package com.gomirai.order.events;

import com.gomirai.common.dto.event.BaseEvent;  // ✅ From common-lib

import java.util.UUID;

/**
 * Order Created Event
 * Extends BaseEvent for common fields (id, timestamp, eventType, version)
 */
public class OrderCreatedEvent extends BaseEvent {
    
    private UUID orderId;
    private UUID userId;
    private String status;
    private Double totalAmount;

    // Constructors
    public OrderCreatedEvent() {
        super();
        this.setEventType("ORDER_CREATED");
    }

    public OrderCreatedEvent(UUID orderId, UUID userId, String status, Double totalAmount) {
        super();
        this.setEventType("ORDER_CREATED");
        this.orderId = orderId;
        this.userId = userId;
        this.status = status;
        this.totalAmount = totalAmount;
    }

    // Getters and Setters
    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}
```

**📝 BaseEvent provides:**
- `id` (UUID) - Event ID
- `timestamp` (Long) - Event timestamp
- `eventType` (String) - Event type identifier
- `version` (String) - Event schema version

### 6.2. Event Producer

```java
package com.gomirai.order.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.order.events.OrderCreatedEvent;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
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
        log.info("Sending OrderCreatedEvent: orderId={}, userId={}, timestamp={}", 
            event.getOrderId(), event.getUserId(), event.getTimestamp());
        kafkaTemplate.send(orderCreatedTopic, event.getOrderId().toString(), event);
    }
}
```

### 6.3. Event Consumer

**Consuming common-lib UserRegisteredEvent:**

```java
package com.gomirai.order.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.UserRegisteredEvent;  // ✅ From common-lib
import com.gomirai.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
        topics = "${kafka.topic.user-registered}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleUserRegistered(UserRegisteredEvent event) {  // ✅ From common-lib
        log.info("Received UserRegisteredEvent: userId={}, phoneNumber={}", 
            event.getUserId(), event.getPhoneNumber());
        
        try {
            // Initialize user's order history or settings
            orderService.initializeUserOrders(event.getUserId());
            log.info("Successfully initialized orders for user: {}", event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process user registration event: {}", event.getUserId(), e);
            throw e;  // Kafka will retry
        }
    }
}
```

**📝 Notes:**
- ✅ UserRegisteredEvent is from common-lib (shared across all services)
- ✅ No need to duplicate event classes
- ✅ Type-safe event consumption

---

## 7. Docker Configuration

### 7.1. Dockerfile

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy common library JAR from local Maven repo
# This requires gomirai-common-lib to be installed first (mvn install)
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

### 7.2. application.properties

```properties
spring.application.name=OrderService
server.port=8083

# MongoDB Configuration
spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}
spring.data.mongodb.database=order_db

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
spring.kafka.consumer.group-id=order-service-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer

# ✅ SECURITY: Whitelist specific packages only
# Include common-lib event packages!
spring.kafka.consumer.properties.spring.json.trusted.packages=com.gomirai.order.events,com.gomirai.common.dto.event,com.gomirai.payment.events
spring.kafka.consumer.auto-offset-reset=earliest

# Kafka Topics
kafka.topic.order-created=order-created-event
kafka.topic.order-updated=order-updated-event
kafka.topic.user-registered=user-registered-event
kafka.topic.payment-completed=payment-completed-event

# JWT Security (from common-lib)
security.jwt.secret=${SECURITY_JWT_SECRET}
security.jwt.access-ttl-ms=86400000

# ✅ SECURITY: Only expose health endpoint
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=when-authorized
management.endpoint.health.show-components=when-authorized

# Logging
logging.level.com.gomirai.order=INFO
logging.level.com.gomirai.common=INFO
logging.level.com.gomirai.common.security=DEBUG
```

### 7.3. Update docker-compose.yml

```yaml
services:
  # ... existing services ...

  # Order Service
  order-service:
    build:
      context: ./OrderService
      dockerfile: Dockerfile
    container_name: order-service
    # ✅ SECURITY: Không expose port ra ngoài (chỉ internal)
    # ports:
    #   - "8083:8083"
    expose:
      - "8083"  # Internal only
    depends_on:
      consul:
        condition: service_healthy
      kafka:
        condition: service_healthy
    environment:
      SPRING_DATA_MONGODB_URI: ${ORDER_MONGODB_URI}
      SECURITY_JWT_SECRET: ${JWT_SECRET}
      SPRING_CLOUD_CONSUL_HOST: consul
      SPRING_CLOUD_CONSUL_PORT: 8500
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:29092
    networks:
      - gomirai-network
    restart: on-failure
```

### 7.4. Update .env file

```env
# Existing variables...
AUTH_MONGODB_URI=mongodb://...
USER_MONGODB_URI=mongodb://...
JWT_SECRET=BASE64:your-secret-here

# Add new service
ORDER_MONGODB_URI=mongodb://user:pass@mongodb:27017/order_db?authSource=admin
```

---

## 8. Gateway Configuration

### 8.1. Update ProxyController.java

```java
// ApiGateway/src/main/java/com/gomirai/gateway/controller/ProxyController.java

private String mapServiceName(String serviceId) {
    if (serviceId == null || serviceId.isEmpty()) {
        throw new IllegalArgumentException("Service ID cannot be empty");
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
            return "OrderService";  // ✅ ADD THIS
        default:
            // ✅ SECURITY: Reject unknown services
            throw new IllegalArgumentException("Unknown service: " + serviceId);
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
            return "/api/orders";  // ✅ ADD THIS
        default:
            return "/" + serviceId;
    }
}
```

**Access Pattern:**
- Frontend calls: `http://localhost:8080/api/orders`
- Gateway routes to: `http://order-service:8083/api/orders`

---

## 9. Testing & Verification

### 9.1. Build & Run

```bash
# ⭐ STEP 1: Build common library first (if modified)
cd gomirai-common-lib
mvn clean install
cd ..

# STEP 2: Build new service
docker-compose build order-service

# STEP 3: Start all services
docker-compose up -d

# STEP 4: View logs
docker-compose logs -f order-service

# STEP 5: Check if registered with Consul
curl http://localhost:8500/v1/catalog/services | jq

# Should show: "OrderService": []
```

### 9.2. Test Authentication

```bash
# 1. Login to get JWT token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phoneNumber": "0123456789",
    "password": "SecurePass123"
  }'

# Response: {"userId":"xxx","role":"CUSTOMER","token":"eyJhbGc..."}

# 2. Test new service with JWT
TOKEN="eyJhbGc..."

curl -X GET http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN"

# Should return 200 OK if JWT is valid
```

### 9.3. Test Validation (Common-Lib GlobalExceptionHandler)

```bash
# Test validation errors
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'

# Expected Response (from common-lib GlobalExceptionHandler):
# {
#   "timestamp": "2025-11-24T12:00:00.000",
#   "status": 400,
#   "error": "Validation Error",
#   "message": "Request validation failed...",
#   "errors": {
#     "productId": "Product ID is required",
#     "quantity": "Quantity is required"
#   }
# }
```

### 9.4. Test Authorization (Common-Lib SecurityUtils)

```bash
# Test ownership validation
curl -X GET http://localhost:8080/api/orders/OTHER_USER_ORDER_ID \
  -H "Authorization: Bearer YOUR_TOKEN"

# Expected: 403 Forbidden (from common-lib SecurityUtils)
# {
#   "timestamp": "2025-11-24T12:00:00.000",
#   "status": 403,
#   "message": "You are not authorized to access this resource"
# }
```

### 9.5. Verify Common-Lib Components

```bash
# Check logs to verify common-lib components are loaded
docker-compose logs order-service | grep "JwtService"
docker-compose logs order-service | grep "SecurityUtils"
docker-compose logs order-service | grep "GlobalExceptionHandler"

# You should see Spring registering these beans from common-lib
```

---

## 10. Checklist

### Pre-Development
- [ ] Đọc và hiểu kiến trúc hiện tại
- [ ] Đọc `gomirai-common-lib/README.md`
- [ ] Build và install common-lib (`mvn clean install`)
- [ ] Xác định service cần thiết phải có
- [ ] Design API endpoints
- [ ] Design database schema
- [ ] Design Kafka events (extend BaseEvent if needed)

### Development
- [ ] Tạo Spring Boot project với đúng dependencies
- [ ] **Add gomirai-common-lib dependency to pom.xml**
- [ ] **Add @ComponentScan to main class (include com.gomirai.common)**
- [ ] ✅ **Use common-lib JwtAuthenticationFilter (NO custom filter)**
- [ ] ✅ **Use common-lib SecurityUtils (NO custom SecurityUtils)**
- [ ] ✅ **Use common-lib GlobalExceptionHandler (NO custom handler)**
- [ ] ✅ **Use common-lib ErrorResponse (NO custom error DTO)**
- [ ] ✅ **Use common-lib exceptions (NotFoundException, ForbiddenException)**
- [ ] ✅ **Extend common-lib BaseEvent for Kafka events**
- [ ] ✅ **Use common-lib Role enum (NO custom Role class)**
- [ ] Implement service-specific DTOs with validation
- [ ] Implement SecurityConfig (autowire common-lib components)
- [ ] Implement Kafka Producer/Consumer (use common-lib events)
- [ ] Implement Business Logic (use common-lib SecurityUtils)
- [ ] Add proper logging
- [ ] Write unit tests

### Configuration
- [ ] Tạo Dockerfile theo chuẩn multi-stage build
- [ ] Update docker-compose.yml (không expose ports ra ngoài)
- [ ] Update .env với MongoDB URI
- [ ] Configure application.properties đầy đủ
- [ ] Configure Kafka trusted packages (include com.gomirai.common.dto.event)
- [ ] Configure Actuator (chỉ health endpoint)
- [ ] Update Gateway ProxyController (whitelist service)

### Testing
- [ ] Test JWT authentication (common-lib JwtService)
- [ ] Test authorization (common-lib SecurityUtils)
- [ ] Test validation errors (common-lib GlobalExceptionHandler)
- [ ] Test error responses format (common-lib ErrorResponse)
- [ ] Test Kafka events (common-lib BaseEvent)
- [ ] Test CORS
- [ ] Test health endpoint
- [ ] Verify service registered với Consul
- [ ] Verify common-lib beans are loaded (check logs)

### Security Review
- [ ] JWT secret từ environment variable
- [ ] Tất cả endpoints require authentication (trừ health)
- [ ] Authorization checks using common-lib SecurityUtils
- [ ] Validation cho tất cả inputs
- [ ] Using common-lib exceptions (no sensitive data leak)
- [ ] CORS properly configured
- [ ] Security headers enabled
- [ ] Actuator endpoints secured
- [ ] Kafka trusted packages include common-lib
- [ ] Service port không exposed ra ngoài

### Documentation
- [ ] Update API documentation
- [ ] Update Kafka events documentation
- [ ] Update this guide nếu có thay đổi
- [ ] Document any new common-lib components (if added)

---

## 11. Troubleshooting

### Service không start

```bash
# Check logs
docker-compose logs order-service

# Common issues:
# - Missing JWT_SECRET in .env
# - Wrong MongoDB URI
# - Kafka not ready
# - common-lib not installed (run mvn install in gomirai-common-lib)
# - Missing @ComponentScan for com.gomirai.common
```

### Common-lib beans not found

```bash
# Error: "No qualifying bean of type 'com.gomirai.common.security.JwtService'"

# Solution 1: Check @ComponentScan
@ComponentScan(basePackages = {
    "com.gomirai.order",
    "com.gomirai.common"  // ⭐ MUST include this!
})

# Solution 2: Rebuild common-lib
cd gomirai-common-lib
mvn clean install
cd ..

# Solution 3: Check dependency in pom.xml
<dependency>
    <groupId>com.gomirai</groupId>
    <artifactId>gomirai-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

### JWT Authentication fail

```bash
# Verify JWT_SECRET is same across all services
docker-compose exec order-service env | grep JWT_SECRET
docker-compose exec auth-service env | grep JWT_SECRET

# Should be identical!
```

### Kafka Consumer không nhận message (common-lib events)

```bash
# Check trusted packages includes common-lib
spring.kafka.consumer.properties.spring.json.trusted.packages=com.gomirai.order.events,com.gomirai.common.dto.event

# ⭐ Must include: com.gomirai.common.dto.event
```

### Service không register với Consul

```bash
# Check Consul
curl http://localhost:8500/v1/catalog/services

# Verify application.properties:
# - spring.cloud.consul.discovery.enabled=true
# - spring.cloud.consul.discovery.register=true
# - Health check path correct
```

### Validation errors không đúng format

```bash
# Verify:
# - @ComponentScan includes com.gomirai.common
# - @Valid annotation in controller
# - Common-lib GlobalExceptionHandler is loaded (check logs)
```

---

## 12. Best Practices Summary

### Using Common Library ⭐
1. **Always add dependency** - Include gomirai-common-lib in pom.xml
2. **Always @ComponentScan** - Scan com.gomirai.common packages
3. **Never duplicate** - Don't create JwtService, SecurityUtils, GlobalExceptionHandler
4. **Use common exceptions** - NotFoundException, ForbiddenException, etc.
5. **Extend BaseEvent** - For all Kafka events
6. **Use common enums** - Role, AuthProvider, ServiceName
7. **Include in trusted packages** - com.gomirai.common.dto.event

### Security ✅
1. Autowire common-lib JwtAuthenticationFilter
2. Use common-lib SecurityUtils for authorization
3. Whitelist services trong Gateway
4. Không expose backend service ports
5. Secure actuator endpoints
6. CORS properly configured
7. Security headers enabled
8. Không log sensitive data

### Validation ✅
1. @Valid cho tất cả request DTOs
2. Use common-lib ValidationUtil for custom validation
3. Common-lib GlobalExceptionHandler handles all errors
4. Throw common-lib exceptions

### Kafka ✅
1. Extend common-lib BaseEvent
2. Use common-lib UserRegisteredEvent (and others)
3. Whitelist trusted packages (include common-lib)
4. Unique groupId cho mỗi service
5. Throw exception để Kafka retry
6. Log chi tiết events

### Docker ✅
1. Multi-stage build
2. Không expose backend ports
3. Use environment variables
4. Proper healthchecks
5. restart: on-failure

### Logging ✅
1. Use @Slf4j
2. Log INFO cho business events
3. Log ERROR cho exceptions
4. Log DEBUG cho security (development only)
5. Log common-lib components loading

---

**🎉 Hoàn Thành!**

Sau khi follow guide này, bạn sẽ có một service mới:
- ✅ **Using gomirai-common-lib** - No code duplication
- ✅ Secure với JWT authentication (common-lib)
- ✅ Authorization validation (common-lib SecurityUtils)
- ✅ Proper input validation
- ✅ Consistent error responses (common-lib)
- ✅ Kafka integration (common-lib events)
- ✅ Docker containerized
- ✅ Registered với Consul
- ✅ Routed qua API Gateway

**Key Takeaway:**  
**Don't reinvent the wheel! Use gomirai-common-lib for all shared components!** 🚀

**Lưu ý:** Luôn test kỹ security trước khi deploy production!

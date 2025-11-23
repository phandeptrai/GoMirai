# 🚀 Hướng Dẫn Thêm Service Mới Vào GoMirai

**Version:** 2.0  
**Last Updated:** 2025-11-23  
**Difficulty:** Intermediate  

Tài liệu này hướng dẫn **CHI TIẾT** cách thêm một microservice mới vào hệ thống GoMirai với **ĐẦY ĐỦ** security, validation, error handling, và best practices.

---

## 📋 Mục Lục

1. [Prerequisites](#1-prerequisites)
2. [Tạo Service Mới](#2-tạo-service-mới)
3. [Security Configuration](#3-security-configuration)
4. [Validation & Error Handling](#4-validation--error-handling)
5. [Kafka Integration](#5-kafka-integration)
6. [Docker Configuration](#6-docker-configuration)
7. [Gateway Configuration](#7-gateway-configuration)
8. [Testing & Verification](#8-testing--verification)
9. [Checklist](#9-checklist)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Prerequisites

### 1.1. Required Knowledge
- ✅ Spring Boot 3.5+
- ✅ Spring Security 6+
- ✅ JWT Authentication
- ✅ Apache Kafka
- ✅ Docker & Docker Compose
- ✅ MongoDB

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

# Verify .env file exists
ls -la .env

# Verify JWT_SECRET is set
grep JWT_SECRET .env
```

---

## 2. Tạo Service Mới

### 2.1. Spring Initializr Configuration

**Project Settings:**
- **Project:** Maven
- **Language:** Java
- **Spring Boot:** 3.5.7
- **Java:** 21
- **Packaging:** Jar
- **Group:** com.gomirai
- **Artifact:** order-service (example)
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

### 2.2. Cấu Trúc Thư Mục

```
OrderService/
├── Dockerfile
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
└── src/
    ├── main/
    │   ├── java/com/gomirai/order/
    │   │   ├── OrderServiceApplication.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── KafkaConsumerConfig.java (optional)
    │   │   │   └── CorsConfig.java (optional - nếu cần custom)
    │   │   ├── controller/
    │   │   │   └── OrderController.java
    │   │   ├── dto/
    │   │   │   ├── CreateOrderRequest.java
    │   │   │   ├── UpdateOrderRequest.java
    │   │   │   ├── OrderResponse.java
    │   │   │   ├── ErrorResponse.java
    │   │   │   └── ValidationErrorResponse.java
    │   │   ├── events/
    │   │   │   ├── OrderCreatedEvent.java
    │   │   │   └── PaymentCompletedEvent.java (consume)
    │   │   ├── exception/
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── OrderNotFoundException.java
    │   │   ├── messaging/
    │   │   │   ├── OrderEventsProducer.java
    │   │   │   └── PaymentEventConsumer.java
    │   │   ├── model/
    │   │   │   ├── Order.java
    │   │   │   └── OrderStatus.java
    │   │   ├── repository/
    │   │   │   └── OrderRepository.java
    │   │   ├── security/
    │   │   │   ├── JwtService.java
    │   │   │   ├── JwtAuthenticationFilter.java
    │   │   │   ├── JwtAuthenticationEntryPoint.java
    │   │   │   └── SecurityUtils.java
    │   │   └── service/
    │   │       └── OrderService.java
    │   └── resources/
    │       └── application.properties
    └── test/
        └── java/com/gomirai/order/
            └── OrderServiceApplicationTests.java
```

### 2.3. pom.xml

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
        <jjwt.version>0.11.5</jjwt.version>
    </properties>
    
    <dependencies>
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
        
        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        
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

## 3. Security Configuration

### 3.1. JwtService.java

```java
package com.gomirai.order.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(@Value("${security.jwt.secret}") String secret) {
        // ✅ IMPORTANT: Xử lý key GIỐNG HỆT AuthService
        byte[] secretBytes = secret.startsWith("BASE64:")
            ? Decoders.BASE64.decode(secret.substring("BASE64:".length()))
            : secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Optional.of(claims);
        } catch (Exception e) {
            // Token invalid hoặc expired
            return Optional.empty();
        }
    }
}
```

### 3.2. JwtAuthenticationFilter.java

```java
package com.gomirai.order.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Optional<Claims> claimsOpt = jwtService.parseToken(token);
                
                if (claimsOpt.isPresent()) {
                    Claims claims = claimsOpt.get();
                    String userIdStr = claims.getSubject();
                    String role = claims.get("role", String.class);
                    
                    if (userIdStr != null) {
                        try {
                            UUID userId = UUID.fromString(userIdStr);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) 
                                                 : Collections.emptyList()
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid UUID format in token: {}", userIdStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot authenticate user from token: {}", e.getMessage(), e);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

### 3.3. JwtAuthenticationEntryPoint.java

```java
package com.gomirai.order.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                        AuthenticationException authException) throws IOException, ServletException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
    }
}
```

### 3.4. SecurityUtils.java

```java
package com.gomirai.order.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Utility class for security operations
 * Use this to validate ownership and authorization
 */
@Component
public class SecurityUtils {

    /**
     * Get current authenticated user ID from JWT token
     */
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }
        
        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        
        throw new SecurityException("Invalid authentication principal");
    }

    /**
     * Validate that current user is the owner of the resource
     */
    public void validateOwnership(UUID resourceUserId) {
        UUID currentUserId = getCurrentUserId();
        if (!currentUserId.equals(resourceUserId)) {
            throw new SecurityException("You are not authorized to access this resource");
        }
    }

    /**
     * Check if current user has ADMIN role
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Validate ownership or admin access
     */
    public void validateOwnershipOrAdmin(UUID resourceUserId) {
        if (!isAdmin()) {
            validateOwnership(resourceUserId);
        }
    }
}
```

### 3.5. SecurityConfig.java

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

import com.gomirai.order.security.JwtAuthenticationEntryPoint;
import com.gomirai.order.security.JwtAuthenticationFilter;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // Enable @PreAuthorize
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, 
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
            .xssProtection(xss -> {})  // XSS Protection deprecated in Spring Security 6.1+
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

---

## 4. Validation & Error Handling

### 4.1. ErrorResponse.java

```java
package com.gomirai.order.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class ErrorResponse {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    private int status;
    private String message;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.message = message;
    }

    // Getters and Setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
```

### 4.2. ValidationErrorResponse.java

```java
package com.gomirai.order.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

public class ValidationErrorResponse {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private Map<String, String> errors;  // Field-level errors

    public ValidationErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ValidationErrorResponse(int status, String error, String message, Map<String, String> errors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.errors = errors;
    }

    // Getters and Setters
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Map<String, String> getErrors() { return errors; }
    public void setErrors(Map<String, String> errors) { this.errors = errors; }
}
```

### 4.3. GlobalExceptionHandler.java

```java
package com.gomirai.order.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gomirai.order.dto.ErrorResponse;
import com.gomirai.order.dto.ValidationErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation errors from @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        logger.warn("Validation error occurred: {} errors", ex.getBindingResult().getErrorCount());
        
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
            logger.debug("Validation error - {}: {}", fieldName, errorMessage);
        });

        ValidationErrorResponse error = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Error",
            "Request validation failed. Please check the errors for each field.",
            fieldErrors
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException e) {
        logger.warn("Resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, e.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurityException(SecurityException e) {
        logger.warn("Security exception: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse(403, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        logger.error("Unexpected error: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "An unexpected error occurred. Please try again later."));
    }
}
```

### 4.4. Example DTO with Validation

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

### 4.5. Controller with @Valid

```java
package com.gomirai.order.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // ✅ @Valid triggers validation
        // If validation fails, GlobalExceptionHandler handles it
        return ResponseEntity.ok(orderService.createOrder(request));
    }
}
```

---

## 5. Kafka Integration

### 5.1. Event Definition (Producer)

```java
package com.gomirai.order.events;

import java.util.UUID;

/**
 * Order Created Event
 * Use record for immutability (best practice)
 */
public record OrderCreatedEvent(
    UUID orderId,
    UUID userId,
    String status,
    Double totalAmount,
    Long createdAt
) {}
```

### 5.2. Event Producer

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
        log.info("Sending OrderCreatedEvent: orderId={}, userId={}", 
            event.orderId(), event.userId());
        kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), event);
    }
}
```

### 5.3. Event Consumer (if needed)

```java
package com.gomirai.order.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.order.events.PaymentCompletedEvent;
import com.gomirai.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;

    @KafkaListener(
        topics = "${kafka.topic.payment-completed}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: orderId={}", event.orderId());
        
        try {
            orderService.updateOrderStatus(event.orderId(), "PAID");
            log.info("Successfully updated order status for orderId: {}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to process payment event for orderId: {}", event.orderId(), e);
            throw e;  // Kafka will retry
        }
    }
}
```

---

## 6. Docker Configuration

### 6.1. Dockerfile

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

### 6.2. application.properties

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
spring.kafka.consumer.properties.spring.json.trusted.packages=com.gomirai.order.events,com.gomirai.payment.events
spring.kafka.consumer.auto-offset-reset=earliest

# Kafka Topics
kafka.topic.order-created=order-created-event
kafka.topic.order-updated=order-updated-event
kafka.topic.payment-completed=payment-completed-event

# JWT Security
security.jwt.secret=${SECURITY_JWT_SECRET}

# ✅ SECURITY: Only expose health endpoint
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=when-authorized
management.endpoint.health.show-components=when-authorized

# Logging
logging.level.com.gomirai.order=INFO
logging.level.com.gomirai.order.security=DEBUG
```

### 6.3. Update docker-compose.yml

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

### 6.4. Update .env file

```env
# Existing variables...
AUTH_MONGODB_URI=mongodb://...
USER_MONGODB_URI=mongodb://...
JWT_SECRET=BASE64:your-secret-here

# Add new service
ORDER_MONGODB_URI=mongodb://user:pass@host/order_db
```

---

## 7. Gateway Configuration

### 7.1. Update ProxyController.java

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

## 8. Testing & Verification

### 8.1. Build & Run

```bash
# Build new service
docker-compose build order-service

# Start all services
docker-compose up -d

# View logs
docker-compose logs -f order-service

# Check if registered with Consul
curl http://localhost:8500/v1/catalog/services | jq
```

### 8.2. Test Authentication

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

### 8.3. Test Validation

```bash
# Test validation errors
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}'

# Expected Response:
# {
#   "timestamp": "2025-11-23T12:00:00.000",
#   "status": 400,
#   "error": "Validation Error",
#   "message": "Request validation failed...",
#   "errors": {
#     "productId": "Product ID is required",
#     "quantity": "Quantity is required"
#   }
# }
```

### 8.4. Test Kafka Events

```bash
# Enter Kafka container
docker exec -it kafka bash

# List topics
kafka-topics --bootstrap-server localhost:9092 --list

# Monitor events
kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic order-created-event \
  --from-beginning

# Check consumer groups
kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe \
  --group order-service-group
```

### 8.5. Verify Security

```bash
# Test without JWT - should fail
curl -X GET http://localhost:8080/api/orders
# Expected: 401 Unauthorized

# Test with invalid JWT - should fail
curl -X GET http://localhost:8080/api/orders \
  -H "Authorization: Bearer invalid-token"
# Expected: 401 Unauthorized

# Test authorization - User A cannot access User B's orders
curl -X GET http://localhost:8080/api/orders/USER_B_ORDER_ID \
  -H "Authorization: Bearer USER_A_TOKEN"
# Expected: 403 Forbidden
```

---

## 9. Checklist

### Pre-Development
- [ ] Đọc và hiểu kiến trúc hiện tại
- [ ] Xác định service cần thiết phải có
- [ ] Design API endpoints
- [ ] Design database schema
- [ ] Design Kafka events

### Development
- [ ] Tạo Spring Boot project với đúng dependencies
- [ ] Implement JWT Authentication (JwtService, Filter, EntryPoint)
- [ ] Implement SecurityUtils cho authorization
- [ ] Implement SecurityConfig với CORS và security headers
- [ ] Implement Validation cho tất cả DTOs
- [ ] Implement GlobalExceptionHandler với ValidationErrorResponse
- [ ] Implement ErrorResponse và ValidationErrorResponse
- [ ] Implement Kafka Producer (nếu cần)
- [ ] Implement Kafka Consumer (nếu cần)
- [ ] Implement Business Logic
- [ ] Add proper logging
- [ ] Write unit tests

### Configuration
- [ ] Tạo Dockerfile theo chuẩn multi-stage build
- [ ] Update docker-compose.yml (không expose ports ra ngoài)
- [ ] Update .env với MongoDB URI
- [ ] Configure application.properties đầy đủ
- [ ] Configure Kafka trusted packages (whitelist)
- [ ] Configure Actuator (chỉ health endpoint)
- [ ] Update Gateway ProxyController (whitelist service)

### Testing
- [ ] Test JWT authentication
- [ ] Test authorization (ownership validation)
- [ ] Test validation errors
- [ ] Test Kafka events (nếu có)
- [ ] Test CORS
- [ ] Test error responses format
- [ ] Test health endpoint
- [ ] Verify service registered với Consul

### Security Review
- [ ] JWT secret từ environment variable
- [ ] Tất cả endpoints require authentication (trừ health)
- [ ] Authorization checks cho resource access
- [ ] Validation cho tất cả inputs
- [ ] Error messages không leak sensitive info
- [ ] CORS properly configured
- [ ] Security headers enabled
- [ ] Actuator endpoints secured
- [ ] Kafka trusted packages whitelisted
- [ ] Service port không exposed ra ngoài

### Documentation
- [ ] Update API documentation
- [ ] Update Kafka events documentation
- [ ] Update this guide nếu có thay đổi

---

## 10. Troubleshooting

### Service không start

```bash
# Check logs
docker-compose logs order-service

# Common issues:
# - Missing JWT_SECRET in .env
# - Wrong MongoDB URI
# - Kafka not ready
# - Port conflict (nếu exposed)
```

### JWT Authentication fail

```bash
# Verify JWT_SECRET is same across all services
docker-compose exec order-service env | grep JWT_SECRET
docker-compose exec auth-service env | grep JWT_SECRET

# Should be identical!
```

### Kafka Consumer không nhận message

```bash
# Check consumer group
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group order-service-group

# Check if topic exists
kafka-topics --bootstrap-server localhost:9092 --list

# Check consumer configuration
# - groupId unique?
# - topic name correct?
# - trusted packages include event package?
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
# - @Valid annotation in controller
# - GlobalExceptionHandler có handle MethodArgumentNotValidException
# - ValidationErrorResponse được return đúng
```

### CORS errors

```bash
# Verify SecurityConfig:
# - corsConfigurationSource() configured
# - allowedOrigins includes frontend URL
# - allowedMethods includes needed methods
```

---

## 11. Best Practices Summary

### Security ✅
1. Luôn validate JWT token
2. Use SecurityUtils để validate ownership
3. Whitelist services trong Gateway
4. Không expose backend service ports
5. Secure actuator endpoints
6. CORS properly configured
7. Security headers enabled
8. Không log sensitive data

### Validation ✅
1. @Valid cho tất cả request DTOs
2. Clear validation messages
3. ValidationErrorResponse với field-level errors
4. Handle all exception types

### Kafka ✅
1. Whitelist trusted packages (không dùng "*")
2. Use record classes cho events (immutable)
3. Unique groupId cho mỗi service
4. Throw exception để Kafka retry
5. Log chi tiết events

### Error Handling ✅
1. GlobalExceptionHandler cho tất cả exceptions
2. Consistent error response format
3. Không leak sensitive information
4. Proper HTTP status codes

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

---

**🎉 Hoàn Thành!**

Sau khi follow guide này, bạn sẽ có một service mới:
- ✅ Secure với JWT authentication
- ✅ Authorization validation
- ✅ Proper input validation
- ✅ Consistent error responses
- ✅ Kafka integration (nếu cần)
- ✅ Docker containerized
- ✅ Registered với Consul
- ✅ Routed qua API Gateway

**Lưu ý:** Luôn test kỹ security trước khi deploy production!

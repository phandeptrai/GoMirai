# 🚀 GoMirai Microservices Platform

> Event-Driven Microservices Architecture với Saga Pattern, Kafka, Consul, và Docker

---

## 📋 MỤC LỤC

1. [Tổng quan](#-tổng-quan)
2. [Kiến trúc hệ thống](#-kiến-trúc-hệ-thống)
3. [Cấu trúc thư mục](#-cấu-trúc-thư-mục)
4. [Công nghệ sử dụng](#-công-nghệ-sử-dụng)
5. [Cài đặt và Cấu hình](#-cài-đặt-và-cấu-hình)
6. [Event-Driven Architecture](#-event-driven-architecture)
7. [Saga Pattern](#-saga-pattern)
8. [Docker & Docker Compose](#-docker--docker-compose)
9. [Environment Variables](#-environment-variables)
10. [API Documentation](#-api-documentation)
11. [Testing](#-testing)
12. [Monitoring](#-monitoring)
13. [Troubleshooting](#-troubleshooting)

---

## 🎯 TỔNG QUAN

GoMirai là một hệ thống microservices hiện đại được xây dựng trên Spring Boot, sử dụng:

- **Event-Driven Architecture** để giao tiếp giữa các services
- **Saga Pattern** để quản lý distributed transactions
- **Service Discovery** với Consul
- **Message Broker** với Apache Kafka
- **API Gateway** để routing và load balancing
- **Docker** để containerization

### Các Services chính:

- **API Gateway** (:8080) - Entry point và routing
- **AuthService** (:8081) - Authentication và Authorization
- **UserService** (:8082) - Quản lý người dùng

---

## 🏗️ KIẾN TRÚC HỆ THỐNG

```
┌────────────────────────────────────────────────────────────┐
│                    External Requests                        │
└──────────────────────────┬─────────────────────────────────┘
                           │
                           ▼
                   ┌───────────────┐
                   │  API Gateway  │ :8080
                   │   (Routing)   │
                   └───────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
      ┌──────────────┐          ┌──────────────┐
      │ AuthService  │          │ UserService  │
      │    :8081     │          │    :8082     │
      └──────┬───────┘          └──────┬───────┘
             │                         │
             │    ┌─────────────┐      │
             └───▶│    Kafka    │◀─────┘
                  │ (Events Bus)│
                  └─────────────┘
             │                         │
             ▼                         ▼
      ┌──────────────┐          ┌──────────────┐
      │ MongoDB      │          │ MongoDB      │
      │ (auth_db)    │          │ (user_db)    │
      └──────────────┘          └──────────────┘

            ┌──────────────┐
            │    Consul    │
            │   :8500      │
            │ (Discovery)  │
            └──────────────┘
```

### Flow giao tiếp:

1. **Synchronous**: Client → API Gateway → Service
2. **Asynchronous**: Service A → Kafka Event → Service B

---

## 📁 CẤU TRÚC THƯ MỤC

```
GoMirai/
├── .env                           # Environment variables (KHÔNG commit)
├── .env.example                   # Template cho .env
├── .gitignore                     # Git ignore rules
├── docker-compose.yml             # Docker orchestration
├── README.md                      # Documentation chính
├── ADD_NEW_SERVICE.md            # Hướng dẫn thêm service mới
│
├── ApiGateway/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/com/gomirai/gateway/
│           │   └── ApiGatewayApplication.java
│           └── resources/
│               └── application.properties
│
├── AuthService/
│   ├── Dockerfile
│   ├── .dockerignore
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/com/gomirai/auth/
│           │   ├── AuthServiceApplication.java
│           │   ├── controller/
│           │   │   └── AuthController.java
│           │   ├── service/
│           │   │   └── AuthService.java
│           │   ├── repository/
│           │   │   └── UserCredentialRepository.java
│           │   ├── model/
│           │   │   └── UserCredential.java
│           │   ├── dto/
│           │   │   ├── LoginRequest.java
│           │   │   ├── LoginResponse.java
│           │   │   └── RegisterRequest.java
│           │   ├── event/
│           │   │   ├── UserCreatedEvent.java
│           │   │   └── SagaCompensationEvent.java
│           │   ├── publisher/
│           │   │   └── AuthEventPublisher.java
│           │   ├── consumer/
│           │   │   └── UserEventConsumer.java
│           │   ├── saga/
│           │   │   └── AuthSagaOrchestrator.java
│           │   └── config/
│           │       ├── KafkaProducerConfig.java
│           │       └── KafkaConsumerConfig.java
│           └── resources/
│               └── application.properties
│
└── UserService/
    ├── Dockerfile
    ├── .dockerignore
    ├── pom.xml
    └── src/
        └── main/
            ├── java/com/gomirai/user/
            │   ├── UserServiceApplication.java
            │   ├── controller/
            │   │   └── UserController.java
            │   ├── service/
            │   │   └── UserService.java
            │   ├── repository/
            │   │   └── UserRepository.java
            │   ├── model/
            │   │   └── User.java
            │   ├── dto/
            │   │   ├── CreateUserRequest.java
            │   │   ├── UpdateUserRequest.java
            │   │   └── UserResponse.java
            │   ├── event/
            │   │   ├── UserCreatedEvent.java
            │   │   ├── UserUpdatedEvent.java
            │   │   └── SagaCompensationEvent.java
            │   ├── publisher/
            │   │   └── UserEventPublisher.java
            │   ├── consumer/
            │   │   └── CompensationConsumer.java
            │   ├── saga/
            │   │   └── UserSagaOrchestrator.java
            │   └── config/
            │       ├── KafkaProducerConfig.java
            │       └── KafkaConsumerConfig.java
            └── resources/
                └── application.properties
```

---

## 🛠️ CÔNG NGHỆ SỬ DỤNG

### Backend Framework
- **Spring Boot 3.5.7** - Core framework
- **Spring Cloud 2025.0.0** - Microservices support
- **Java 21** - Programming language

### Service Discovery & Config
- **Consul** - Service registry và health checking
- **Spring Cloud Consul** - Consul integration

### Message Broker
- **Apache Kafka 7.5.0** - Event streaming platform
- **Spring Kafka** - Kafka integration

### API Gateway
- **Spring Cloud Gateway** - Routing và load balancing

### Database
- **MongoDB Atlas** - Cloud NoSQL database
- **Spring Data MongoDB** - MongoDB integration

### Containerization
- **Docker** - Container platform
- **Docker Compose** - Multi-container orchestration

### Monitoring
- **Spring Boot Actuator** - Health checks và metrics
- **Micrometer Prometheus** - Metrics export

---

## ⚙️ CÀI ĐẶT VÀ CẤU HÌNH

### Prerequisites

- **Java 21+**
- **Docker & Docker Compose**
- **Maven 3.8+** (hoặc dùng wrapper)
- **MongoDB Atlas Account** (hoặc local MongoDB)

### Bước 1: Clone Repository

```bash
git clone <repository-url>
cd GoMirai
```

### Bước 2: Cấu hình Environment Variables

```bash
# Copy template
cp .env.example .env

# Sửa .env với thông tin thực
nano .env
```

**Nội dung .env cần cấu hình:**

```bash
# MongoDB Atlas
MONGODB_AUTH_URI=mongodb+srv://username:password@cluster.mongodb.net/auth_db
MONGODB_USER_URI=mongodb+srv://username:password@cluster.mongodb.net/user_db

# Consul
CONSUL_HOST=consul
CONSUL_PORT=8500

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# Service Ports
API_GATEWAY_PORT=8080
AUTH_SERVICE_PORT=8081
USER_SERVICE_PORT=8082
```

### Bước 3: Start Infrastructure

```bash
# Start tất cả services
docker compose up -d

# Hoặc start từng phần
docker compose up -d consul zookeeper kafka    # Infrastructure
docker compose up -d --build                   # All services
```

### Bước 4: Verify Services

```bash
# Check container status
docker compose ps

# Check logs
docker compose logs -f

# Check Consul UI
open http://localhost:8500

# Check health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

---

## 📡 EVENT-DRIVEN ARCHITECTURE

### 1. Định nghĩa Event Model

```java
// UserService/event/UserCreatedEvent.java
package com.gomirai.user.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private String userId;
    private String username;
    private String email;
    private LocalDateTime timestamp;
    private String sagaId;  // For Saga correlation
}
```

### 2. Phát ra Sự kiện (Event Publisher)

#### Config Kafka Producer

```java
// config/KafkaProducerConfig.java
@Configuration
public class KafkaProducerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

#### Publisher Service

```java
// publisher/UserEventPublisher.java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.user-created}")
    private String userCreatedTopic;
    
    public void publishUserCreated(UserCreatedEvent event) {
        log.info("Publishing UserCreatedEvent: {}", event);
        
        kafkaTemplate.send(userCreatedTopic, event.getUserId(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event published successfully, offset: {}", 
                        result.getRecordMetadata().offset());
                } else {
                    log.error("Failed to publish event", ex);
                }
            });
    }
}
```

#### Sử dụng Publisher

```java
// service/UserService.java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;
    
    public UserResponse createUser(CreateUserRequest request) {
        // 1. Validate và tạo user
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        
        // 2. Lưu vào database
        User savedUser = userRepository.save(user);
        
        // 3. Phát sự kiện
        UserCreatedEvent event = new UserCreatedEvent(
            savedUser.getId(),
            savedUser.getUsername(),
            savedUser.getEmail(),
            LocalDateTime.now(),
            UUID.randomUUID().toString()
        );
        
        eventPublisher.publishUserCreated(event);
        
        return UserResponse.from(savedUser);
    }
}
```

### 3. Lắng nghe Sự kiện (Event Consumer)

#### Config Kafka Consumer

```java
// config/KafkaConsumerConfig.java
@EnableKafka
@Configuration
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(config);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

#### Consumer Service

```java
// consumer/UserEventConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {
    
    private final AuthService authService;
    
    @KafkaListener(
        topics = "${kafka.topic.user-created}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("Received UserCreatedEvent: {}", event);
        
        try {
            // Xử lý logic - tạo auth credentials cho user
            authService.createCredentials(event.getUserId(), event.getEmail());
            log.info("Auth credentials created for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Error handling UserCreatedEvent", e);
            // Trigger compensation nếu cần
        }
    }
}
```

### Event Flow Diagram

```
┌──────────────┐                    ┌──────────────┐
│ UserService  │                    │ AuthService  │
│              │                    │              │
│ 1. Save User │                    │              │
│ 2. Publish   │────────────────────▶│ 3. Listen   │
│    Event     │  UserCreatedEvent   │    Event    │
│              │                     │ 4. Create   │
│              │                     │    Auth     │
└──────────────┘                    └──────────────┘
       │                                     │
       │          ┌──────────────┐          │
       └─────────▶│    Kafka     │◀─────────┘
                  │   Topic      │
                  └──────────────┘
```

---

## 🔄 SAGA PATTERN

Saga Pattern được sử dụng để quản lý distributed transactions trong microservices.

### Choreography Saga (Sử dụng trong hệ thống)

Mỗi service tự quyết định action dựa trên events nhận được.

### Flow: Create User Saga

```
┌──────────────────────────────────────────────────────────┐
│              CREATE USER SAGA FLOW                        │
└──────────────────────────────────────────────────────────┘

Step 1: UserService
   ├─ Validate request
   ├─ Save user to database
   └─ Publish UserCreatedEvent
         │
         ▼
Step 2: AuthService (Listen UserCreatedEvent)
   ├─ Receive event
   ├─ Create auth credentials
   ├─ If SUCCESS → Done
   └─ If FAILED  → Publish SagaCompensationEvent
         │
         ▼
Step 3: UserService (Listen SagaCompensationEvent)
   └─ Delete user (Rollback)
```

### 1. Saga Event Model

```java
// event/SagaCompensationEvent.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaCompensationEvent {
    private String sagaId;
    private String userId;
    private String reason;
    private String compensationAction;  // DELETE_USER, ROLLBACK_AUTH, etc.
}
```

### 2. Saga Orchestrator

```java
// saga/UserSagaOrchestrator.java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSagaOrchestrator {
    
    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.saga-compensation}")
    private String compensationTopic;
    
    /**
     * Trigger compensation khi có lỗi
     */
    public void triggerCompensation(String sagaId, String userId, String reason) {
        log.warn("Triggering compensation for saga: {}, reason: {}", sagaId, reason);
        
        SagaCompensationEvent event = new SagaCompensationEvent(
            sagaId,
            userId,
            reason,
            "DELETE_USER"
        );
        
        kafkaTemplate.send(compensationTopic, userId, event);
    }
    
    /**
     * Handle compensation - rollback user creation
     */
    @KafkaListener(topics = "${kafka.topic.saga-compensation}")
    public void handleCompensation(SagaCompensationEvent event) {
        log.warn("Handling compensation: {}", event);
        
        if ("DELETE_USER".equals(event.getCompensationAction())) {
            try {
                userRepository.deleteById(event.getUserId());
                log.info("User {} deleted as compensation", event.getUserId());
            } catch (Exception e) {
                log.error("Failed to compensate user: {}", event.getUserId(), e);
            }
        }
    }
}
```

### 3. Sử dụng Saga trong AuthService

```java
// AuthService/consumer/UserEventConsumer.java
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {
    
    private final AuthService authService;
    private final SagaOrchestrator sagaOrchestrator;
    
    @KafkaListener(topics = "${kafka.topic.user-created}")
    public void handleUserCreated(UserCreatedEvent event) {
        log.info("Processing UserCreatedEvent: {}", event);
        
        try {
            // Tạo auth credentials
            authService.createCredentials(event.getUserId(), event.getEmail());
            log.info("✅ Saga step completed: Auth created for {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("❌ Saga step failed: Cannot create auth", e);
            
            // Trigger compensation
            sagaOrchestrator.compensate(
                event.getSagaId(),
                event.getUserId(),
                "Failed to create auth credentials: " + e.getMessage()
            );
        }
    }
}
```

### Saga State Tracking (Advanced)

```java
// model/SagaState.java
@Document(collection = "saga_states")
@Data
public class SagaState {
    @Id
    private String sagaId;
    private String entityId;
    private String status;  // STARTED, COMPLETED, COMPENSATING, FAILED
    private List<String> completedSteps;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 🐳 DOCKER & DOCKER COMPOSE

### Docker Compose Services

```yaml
services:
  # Infrastructure
  consul:      # Service Discovery - :8500
  zookeeper:   # Kafka dependency - :2181
  kafka:       # Message Broker - :9092
  
  # Application Services
  api-gateway:   # :8080
  auth-service:  # :8081
  user-service:  # :8082
```

### Các lệnh Docker Compose

```bash
# Build tất cả services
docker compose build

# Start tất cả
docker compose up -d

# Start một service cụ thể
docker compose up -d user-service

# Stop tất cả
docker compose stop

# Stop và remove containers
docker compose down

# Stop và remove containers + volumes
docker compose down -v

# Xem logs
docker compose logs -f
docker compose logs -f user-service

# Xem status
docker compose ps

# Restart service
docker compose restart auth-service

# Rebuild và restart
docker compose up -d --build user-service

# Scale service (manual)
docker compose up -d --scale user-service=3

# Exec vào container
docker compose exec user-service bash
```

### Dockerfile Structure

```dockerfile
# Multi-stage build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml mvnw .
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 🔐 ENVIRONMENT VARIABLES

### .env File Structure

```bash
# ============================================
# MongoDB Atlas URIs
# ============================================
MONGODB_AUTH_URI=mongodb+srv://AuthService:password@cluster.mongodb.net/auth_db
MONGODB_USER_URI=mongodb+srv://UserService:password@cluster.mongodb.net/user_db

# ============================================
# Consul Configuration
# ============================================
CONSUL_HOST=consul
CONSUL_PORT=8500

# ============================================
# Kafka Configuration
# ============================================
KAFKA_BOOTSTRAP_SERVERS=kafka:29092

# ============================================
# Service Ports
# ============================================
API_GATEWAY_PORT=8080
AUTH_SERVICE_PORT=8081
USER_SERVICE_PORT=8082
```

### Cách Docker Compose sử dụng .env

```yaml
services:
  user-service:
    environment:
      SPRING_DATA_MONGODB_URI: ${MONGODB_USER_URI}
      #                         ↑
      #                    Đọc từ .env
```

### Cách Spring Boot sử dụng Environment Variables

```properties
# application.properties
spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI:mongodb://localhost:27017/user_db}
#                        ↑                        ↑
#                   Env variable           Default value
```

### Priority Order

```
1. Environment Variables (Docker) ← Highest priority
2. application.properties
3. Default values ← Lowest priority
```

---

## 📚 API DOCUMENTATION

### API Gateway Routes

```
http://localhost:8080/api/auth/**   → AuthService
http://localhost:8080/api/users/**  → UserService
```

### AuthService Endpoints

```bash
# Register
POST /api/auth/register
Content-Type: application/json

{
  "username": "john",
  "email": "john@example.com",
  "password": "password123"
}

# Login
POST /api/auth/login
{
  "email": "john@example.com",
  "password": "password123"
}

# Logout
POST /api/auth/logout
```

### UserService Endpoints

```bash
# Create User
POST /api/users
{
  "username": "jane",
  "email": "jane@example.com",
  "fullName": "Jane Doe"
}

# Get User
GET /api/users/{id}

# Update User
PUT /api/users/{id}
{
  "fullName": "Jane Smith",
  "phone": "+1234567890"
}

# Delete User
DELETE /api/users/{id}
```

---

## 🧪 TESTING

### Test Infrastructure

```bash
# 1. Check containers
docker compose ps

# 2. Test Consul
curl http://localhost:8500/v1/catalog/services

# 3. Test Kafka
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# 4. Test services health
curl http://localhost:8080/actuator/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

### Test Events

```bash
# Producer: Gửi test event
docker exec -it kafka kafka-console-producer \
  --topic user-created-event \
  --bootstrap-server localhost:9092

# Paste JSON event:
{"userId":"test123","username":"john","email":"john@test.com","timestamp":"2025-10-25T10:00:00","sagaId":"saga-123"}

# Consumer: Đọc events
docker exec kafka kafka-console-consumer \
  --topic user-created-event \
  --bootstrap-server localhost:9092 \
  --from-beginning
```

### Test API

```bash
# Via Gateway
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com"
  }'

# Direct to service
curl -X POST http://localhost:8082/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com"
  }'
```

---

## 📊 MONITORING

### Health Checks

```bash
# Detailed health
curl http://localhost:8082/actuator/health | jq

# Components
curl http://localhost:8082/actuator/health | jq '.components | keys'

# MongoDB status
curl http://localhost:8082/actuator/health | jq '.components.mongo'

# Consul status
curl http://localhost:8082/actuator/health | jq '.components.consul'
```

### Metrics

```bash
# Available metrics
curl http://localhost:8082/actuator/metrics

# JVM metrics
curl http://localhost:8082/actuator/metrics/jvm.memory.used

# Kafka metrics
curl http://localhost:8082/actuator/metrics/kafka.consumer.fetch.manager.records.lag
```

### Consul UI

```
http://localhost:8500
```

Services tab → Xem tất cả registered services và health status

---

## 🔧 TROUBLESHOOTING

### Service không start

```bash
# Check logs
docker compose logs service-name

# Check environment variables
docker compose exec service-name env | grep MONGO

# Restart service
docker compose restart service-name
```

### MongoDB connection failed

```bash
# Verify MongoDB URI
docker compose exec user-service env | grep MONGODB_URI

# Check MongoDB Atlas Network Access
# → Whitelist IP hoặc allow 0.0.0.0/0
```

### Kafka connection failed

```bash
# Test Kafka connectivity
docker compose exec user-service nc -zv kafka 29092

# Check Kafka logs
docker compose logs kafka | tail -50
```

### Service không đăng ký với Consul

```bash
# Test Consul connectivity
docker compose exec user-service ping consul

# Check Consul logs
docker compose logs consul | tail -50

# Verify application.properties
docker compose exec user-service cat /app/application.properties | grep consul
```

### Events không được consume

```bash
# Check consumer group
docker exec kafka kafka-consumer-groups --list --bootstrap-server localhost:9092

# Check consumer lag
docker exec kafka kafka-consumer-groups --describe \
  --group user-service-group \
  --bootstrap-server localhost:9092

# Check service logs
docker compose logs user-service | grep -i kafka
```

---

## 🚀 PRODUCTION DEPLOYMENT

Hệ thống đã được thiết kế sẵn để migrate sang **Kubernetes** dễ dàng.

### Migration Steps

1. ✅ Code không cần thay đổi
2. ✅ Convert docker-compose.yml → Kubernetes manifests
3. ✅ Setup ConfigMaps & Secrets
4. ✅ Deploy với Horizontal Pod Autoscaler (HPA)

Xem thêm: [Kubernetes Migration Guide](./K8S_MIGRATION.md) *(Coming soon)*

---

## 📖 REFERENCES

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Cloud Consul](https://spring.io/projects/spring-cloud-consul)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Saga Pattern](https://microservices.io/patterns/data/saga.html)
- [12-Factor App](https://12factor.net/)

---

## 👥 CONTRIBUTORS

- **Your Team** - Initial work

---

## 📄 LICENSE

This project is licensed under the MIT License.

---

**🎉 Happy Coding! Build scalable microservices with GoMirai!**


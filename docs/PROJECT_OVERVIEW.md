# 🚀 GoMirai Project Overview & Status

**Project Name:** GoMirai  
**Architecture:** Microservices  
**Version:** 1.0.0  
**Last Updated:** 2025-11-23  
**Status:** ✅ Development - Core Services Completed

---

## 📋 Table of Contents

1. [Project Summary](#1-project-summary)
2. [System Architecture](#2-system-architecture)
3. [Current Services](#3-current-services)
4. [Security Implementation](#4-security-implementation)
5. [Technology Stack](#5-technology-stack)
6. [Project Structure](#6-project-structure)
7. [API Endpoints](#7-api-endpoints)
8. [Database Schema](#8-database-schema)
9. [Development Progress](#9-development-progress)
10. [What's Next](#10-whats-next)
11. [Documentation](#11-documentation)

---

## 1. Project Summary

### 1.1. What is GoMirai?

GoMirai là một hệ thống microservices hiện đại được xây dựng với Spring Boot, sử dụng event-driven architecture với Apache Kafka. Dự án được thiết kế với focus vào **security, scalability, và maintainability**.

### 1.2. Core Features

- ✅ **Authentication & Authorization** - JWT-based auth với role-based access control
- ✅ **User Management** - Quản lý thông tin người dùng và profiles
- ✅ **API Gateway** - Single entry point với service discovery
- ✅ **Event-Driven Communication** - Kafka-based messaging giữa các services
- ✅ **Service Discovery** - Consul-based service registration và discovery
- ✅ **Rate Limiting** - Bảo vệ authentication endpoints
- ✅ **Input Validation** - Comprehensive validation với clear error messages
- ✅ **CORS Support** - Cross-origin requests cho frontend integration

### 1.3. Design Principles

1. **Security First** - Mọi endpoint đều require authentication (trừ login/register)
2. **Zero Trust** - Không có direct access vào backend services, chỉ qua Gateway
3. **Fail Fast** - Validation errors trả về ngay với clear messages
4. **Event-Driven** - Services communicate qua Kafka events
5. **Stateless** - JWT-based authentication, no server-side sessions
6. **Cloud-Ready** - Docker containerized, ready for orchestration

---

## 2. System Architecture

### 2.1. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend (Port 3000)                     │
│                    (React/Angular/Vue/Mobile)                    │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP + JWT
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    API Gateway (Port 8080)                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  • JWT Validation                                         │  │
│  │  • Service Discovery (Consul)                             │  │
│  │  • Request Routing                                        │  │
│  │  • Security Headers                                       │  │
│  │  • CORS Configuration                                     │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────┬───────────────────────┬────────────────────────────┘
             │                       │
    ┌────────▼────────┐     ┌───────▼────────┐
    │  AuthService    │     │  UserService   │
    │   (Port 8081)   │     │   (Port 8082)  │
    │   [Internal]    │     │   [Internal]   │
    └────────┬────────┘     └───────┬────────┘
             │                      │
             └──────────┬───────────┘
                        ▼
            ┌───────────────────────┐
            │   Apache Kafka        │
            │   (Port 9092)         │
            │   Event Bus           │
            └───────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
   ┌─────────┐    ┌─────────┐    ┌─────────┐
   │ MongoDB │    │ MongoDB │    │  Consul │
   │  Auth   │    │  User   │    │  (8500) │
   └─────────┘    └─────────┘    └─────────┘
```

### 2.2. Request Flow

**Authentication Flow:**
```
1. Client → POST /api/auth/register → API Gateway → AuthService
2. AuthService → Save user → MongoDB (auth_db)
3. AuthService → Publish UserRegisteredEvent → Kafka
4. UserService ← Subscribe UserRegisteredEvent ← Kafka
5. UserService → Create UserProfile → MongoDB (user_db)
6. Client ← JWT Token ← AuthService
```

**Authenticated Request Flow:**
```
1. Client → GET /api/users/{userId} + JWT → API Gateway
2. API Gateway → Validate JWT → Extract userId & role
3. API Gateway → Discover service → Consul
4. API Gateway → Forward request → UserService
5. UserService → Validate JWT (again)
6. UserService → Check authorization (ownership/admin)
7. UserService → Fetch data → MongoDB
8. Client ← JSON Response ← UserService
```

### 2.3. Security Layers

```
Layer 1: API Gateway
├─ JWT Validation
├─ Service Whitelist (prevent service discovery bypass)
├─ Security Headers (CSP, X-Frame-Options)
└─ CORS Configuration

Layer 2: Backend Services
├─ JWT Re-validation
├─ Authorization Checks (SecurityUtils)
├─ Input Validation (@Valid)
├─ Rate Limiting (AuthService only)
└─ Error Handling (no sensitive data leak)

Layer 3: Data Layer
├─ MongoDB Authentication
├─ Database-level Access Control
└─ Connection Pooling
```

---

## 3. Current Services

### 3.1. API Gateway

**Status:** ✅ Production Ready  
**Port:** 8080 (External)  
**Purpose:** Single entry point cho tất cả frontend requests

**Responsibilities:**
- JWT token validation
- Service discovery via Consul
- Request routing to backend services
- Security headers injection
- CORS handling
- Error standardization

**Key Features:**
- ✅ JWT authentication filter
- ✅ Service name whitelist (security)
- ✅ Path prefix mapping
- ✅ Security headers (CSP, X-Frame-Options, XSS)
- ✅ CORS configuration
- ✅ Actuator endpoints secured

**Technologies:**
- Spring Boot 3.5.7
- Spring Cloud Gateway
- Spring Security
- Consul Discovery
- JJWT 0.12.3

---

### 3.2. AuthService

**Status:** ✅ Production Ready  
**Port:** 8081 (Internal Only)  
**Database:** MongoDB (auth_db)  
**Purpose:** User authentication & JWT token management

**Responsibilities:**
- User registration with password hashing (BCrypt)
- User login with JWT token generation
- JWT token validation
- Password policy enforcement
- Rate limiting (5 requests/minute)
- Publish UserRegisteredEvent to Kafka

**Key Features:**
- ✅ BCrypt password hashing (strength 10)
- ✅ JWT token generation (24h expiration)
- ✅ Role-based tokens (CUSTOMER, ADMIN)
- ✅ Rate limiting (Bucket4j)
- ✅ Input validation (phone, password strength)
- ✅ Kafka producer (UserRegisteredEvent)
- ✅ Security headers
- ✅ CORS configuration

**Endpoints:**
```
POST /auth/register - Register new user
POST /auth/login    - Login & get JWT token
POST /auth/validate - Validate JWT token (internal)
GET  /actuator/health - Health check
```

**Technologies:**
- Spring Boot 3.5.7
- Spring Security
- MongoDB
- Kafka Producer
- Bucket4j (Rate Limiting)
- JJWT 0.12.3

---

### 3.3. UserService

**Status:** ✅ Production Ready  
**Port:** 8082 (Internal Only)  
**Database:** MongoDB (user_db)  
**Purpose:** User profile management

**Responsibilities:**
- Quản lý user profiles (CRUD)
- Listen to UserRegisteredEvent từ AuthService
- Authorization validation (ownership checks)
- Profile completion status tracking

**Key Features:**
- ✅ JWT authentication
- ✅ Ownership validation (SecurityUtils)
- ✅ Admin-only endpoints
- ✅ Kafka consumer (UserRegisteredEvent)
- ✅ Input validation (phone, email)
- ✅ Comprehensive error handling
- ✅ Security headers
- ✅ CORS configuration

**Endpoints:**
```
GET    /api/users/{userId}  - Get user profile (owner or admin)
PUT    /api/users/{userId}  - Update user profile (owner only)
DELETE /api/users/{userId}  - Delete user profile (owner only)
GET    /api/users           - Get all profiles (admin only)
GET    /actuator/health     - Health check
```

**Technologies:**
- Spring Boot 3.5.7
- Spring Security
- MongoDB
- Kafka Consumer
- Jakarta Validation
- Lombok

---

## 4. Security Implementation

### 4.1. Authentication Flow

**JWT Token Structure:**
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // userId (UUID)
  "role": "CUSTOMER",                              // User role
  "iat": 1700000000,                               // Issued at
  "exp": 1700086400                                // Expires (24h)
}
```

**JWT Secret:**
- Stored in `.env` file
- Format: `BASE64:encoded-secret-here`
- Shared across ALL services
- Minimum 256 bits for HS256

### 4.2. Authorization Strategy

**1. Ownership Validation:**
```java
// User can only access their own resources
securityUtils.validateOwnership(userId);
```

**2. Role-Based Access:**
```java
// Only ADMIN can access
@PreAuthorize("hasRole('ADMIN')")
```

**3. Ownership OR Admin:**
```java
// User can access own resource, or ADMIN can access all
securityUtils.validateOwnershipOrAdmin(userId);
```

### 4.3. Security Measures Implemented

| Security Measure | API Gateway | AuthService | UserService |
|------------------|-------------|-------------|-------------|
| JWT Validation | ✅ | ✅ | ✅ |
| Authorization Checks | ❌ (routing only) | ❌ (auth only) | ✅ |
| Rate Limiting | ❌ | ✅ (5/min) | ❌ |
| Input Validation | ❌ | ✅ | ✅ |
| CORS | ✅ | ✅ | ✅ |
| Security Headers | ✅ | ✅ | ✅ |
| Actuator Security | ✅ (health only) | ✅ (health only) | ✅ (health only) |
| Service Whitelist | ✅ | ❌ | ❌ |
| Password Hashing | ❌ | ✅ (BCrypt) | ❌ |
| Kafka Trusted Packages | ❌ | ✅ | ✅ |

### 4.4. Security Headers

**All services send these headers:**
```
Content-Security-Policy: default-src 'self'
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
```

---

## 5. Technology Stack

### 5.1. Backend Framework
- **Java 21** - LTS version
- **Spring Boot 3.5.7** - Latest stable
- **Spring Cloud 2025.0.0** - Service discovery, config
- **Spring Security 6+** - Authentication & authorization

### 5.2. Databases
- **MongoDB 7.0** - NoSQL database
  - `auth_db` - AuthService data
  - `user_db` - UserService data

### 5.3. Message Broker
- **Apache Kafka 3.x** - Event streaming
  - Topics: `user-registered-event`

### 5.4. Service Discovery
- **Consul 1.15** - Service registry & health checks

### 5.5. API Gateway
- **Spring Cloud Gateway** - Reactive gateway

### 5.6. Security
- **JJWT 0.12.3** - JWT implementation
- **BCrypt** - Password hashing
- **Bucket4j 8.1.0** - Rate limiting

### 5.7. Validation
- **Jakarta Validation** - Bean validation
- **Hibernate Validator** - Validation implementation

### 5.8. Utilities
- **Lombok** - Reduce boilerplate code
- **SLF4J + Logback** - Logging

### 5.9. Containerization
- **Docker** - Container runtime
- **Docker Compose** - Multi-container orchestration

### 5.10. Build Tools
- **Maven 3.9+** - Dependency management & build

---

## 6. Project Structure

```
GoMirai/
├── ApiGateway/                      # API Gateway Service
│   ├── src/main/java/com/gomirai/gateway/
│   │   ├── config/
│   │   │   └── SecurityConfig.java           # Security + CORS config
│   │   ├── controller/
│   │   │   └── ProxyController.java          # Service routing
│   │   └── security/
│   │       ├── JwtService.java               # JWT parsing
│   │       ├── JwtAuthenticationFilter.java  # JWT validation filter
│   │       └── JwtAuthenticationEntryPoint.java
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── Dockerfile
│   └── pom.xml
│
├── AuthService/                     # Authentication Service
│   ├── src/main/java/com/gomirai/auth/
│   │   ├── config/
│   │   │   └── SecurityConfig.java           # Security + Rate limiting
│   │   ├── controller/
│   │   │   └── AuthController.java           # Register, Login, Validate
│   │   ├── dto/
│   │   │   ├── RegisterRequest.java          # With validation
│   │   │   ├── LoginRequest.java
│   │   │   ├── ErrorResponse.java
│   │   │   └── ValidationErrorResponse.java
│   │   ├── events/
│   │   │   └── UserRegisteredEvent.java      # Kafka event
│   │   ├── exception/
│   │   │   └── GlobalExceptionHandler.java
│   │   ├── filter/
│   │   │   └── RateLimitingFilter.java       # Bucket4j rate limiter
│   │   ├── messaging/
│   │   │   └── UserEventsProducer.java       # Kafka producer
│   │   ├── model/
│   │   │   └── User.java                     # MongoDB entity
│   │   ├── repository/
│   │   │   └── UserRepository.java
│   │   ├── security/
│   │   │   ├── JwtService.java               # JWT generation
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── JwtAuthenticationEntryPoint.java
│   │   │   └── RateLimitingService.java      # Rate limiting logic
│   │   └── service/
│   │       └── AuthService.java              # Business logic
│   ├── src/main/resources/
│   │   └── application.properties            # Kafka topics, rate limiting
│   ├── Dockerfile
│   └── pom.xml
│
├── UserService/                     # User Profile Service
│   ├── src/main/java/com/gomirai/user/
│   │   ├── config/
│   │   │   └── SecurityConfig.java           # Security + CORS + Method security
│   │   ├── consumer/
│   │   │   └── UserRegisteredEventConsumer.java  # Kafka consumer
│   │   ├── controller/
│   │   │   └── UserProfileController.java    # CRUD endpoints
│   │   ├── dto/
│   │   │   ├── CreateUserProfileRequest.java # With validation
│   │   │   ├── UpdateUserProfileRequest.java
│   │   │   ├── UserProfileResponse.java
│   │   │   ├── ErrorResponse.java
│   │   │   └── ValidationErrorResponse.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── ForbiddenException.java
│   │   ├── model/
│   │   │   ├── UserProfile.java              # MongoDB entity
│   │   │   └── Address.java                  # Embedded document
│   │   ├── repository/
│   │   │   └── UserProfileRepository.java
│   │   ├── security/
│   │   │   ├── JwtService.java
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── JwtAuthenticationEntryPoint.java
│   │   │   └── SecurityUtils.java            # Authorization helpers
│   │   └── service/
│   │       └── UserProfileService.java       # Business logic
│   ├── src/main/resources/
│   │   └── application.properties            # Kafka consumer config
│   ├── Dockerfile
│   └── pom.xml
│
├── docs/                            # 📚 Documentation
│   ├── PROJECT_OVERVIEW.md          # This file
│   ├── COMMON_ERRORS_TROUBLESHOOTING.md  # Troubleshooting guide
│   └── ADDING_NEW_SERVICE.md        # Guide to add new services
│
├── .env                             # 🔒 Environment variables (NEVER commit)
├── .env.example                     # ✅ Environment template
├── docker-compose.yml               # 🐳 All services orchestration
└── README.md                        # 📖 Project README
```

---

## 7. API Endpoints

### 7.1. Public Endpoints (No JWT Required)

**Authentication:**
```http
POST   http://localhost:8080/api/auth/register
POST   http://localhost:8080/api/auth/login
```

**Health Checks:**
```http
GET    http://localhost:8080/actuator/health        # API Gateway health
GET    http://localhost:8080/api/auth/health        # AuthService health (via gateway)
GET    http://localhost:8080/api/users/health       # UserService health (via gateway)
```

### 7.2. Protected Endpoints (JWT Required)

**User Management:**
```http
GET    http://localhost:8080/api/users/{userId}     # Get profile (owner or admin)
PUT    http://localhost:8080/api/users/{userId}     # Update profile (owner only)
DELETE http://localhost:8080/api/users/{userId}     # Delete profile (owner only)
GET    http://localhost:8080/api/users              # List all users (admin only)
```

**Authorization Header:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 7.3. Internal Endpoints (Not Exposed)

```http
POST   http://auth-service:8081/auth/validate      # JWT validation (Gateway internal)
```

---

## 8. Database Schema

### 8.1. AuthService - auth_db

**Collection: users**
```javascript
{
  "_id": ObjectId("..."),
  "userId": UUID("550e8400-e29b-41d4-a716-446655440000"),
  "phoneNumber": "0123456789",           // Unique, indexed
  "password": "$2a$10$...",               // BCrypt hashed
  "role": "CUSTOMER",                     // CUSTOMER | ADMIN
  "createdAt": ISODate("2025-11-23T..."),
  "updatedAt": ISODate("2025-11-23T...")
}
```

**Indexes:**
- `phoneNumber` (unique)
- `userId` (unique)

### 8.2. UserService - user_db

**Collection: user_profiles**
```javascript
{
  "_id": UUID("550e8400-e29b-41d4-a716-446655440000"),  // Same as userId from auth
  "fullName": "Nguyen Van A",
  "phone": "0123456789",
  "email": "example@email.com",
  "address": {
    "street": "123 Main St",
    "city": "Ho Chi Minh",
    "state": "HCM",
    "zipCode": "70000",
    "country": "Vietnam"
  },
  "dateOfBirth": ISODate("1990-01-01T...")
}
```

**Indexes:**
- `_id` (primary key = userId)

---

## 9. Development Progress

### 9.1. Completed Features ✅

#### Infrastructure
- [x] Docker Compose setup với tất cả services
- [x] Consul service discovery
- [x] Kafka message broker
- [x] MongoDB databases (auth_db, user_db)
- [x] Multi-stage Dockerfile cho mỗi service

#### Security
- [x] JWT authentication implementation
- [x] JWT validation ở Gateway và backend services
- [x] Authorization với SecurityUtils
- [x] BCrypt password hashing
- [x] Rate limiting cho auth endpoints
- [x] Security headers (CSP, X-Frame-Options)
- [x] CORS configuration
- [x] Service whitelist ở Gateway
- [x] Actuator endpoints secured
- [x] Kafka trusted packages whitelist
- [x] Backend ports không exposed (internal only)

#### Validation & Error Handling
- [x] Input validation với Jakarta Validation
- [x] Custom validation messages
- [x] Global exception handler cho tất cả services
- [x] Standardized ErrorResponse
- [x] Detailed ValidationErrorResponse với field-level errors
- [x] No sensitive data leak trong error messages

#### Services
- [x] API Gateway với routing và security
- [x] AuthService với register/login/validate
- [x] UserService với CRUD operations
- [x] Kafka integration (producer/consumer)
- [x] Event-driven user profile creation

#### Documentation
- [x] README.md
- [x] ADDING_NEW_SERVICE.md - Complete guide
- [x] COMMON_ERRORS_TROUBLESHOOTING.md - Error reference
- [x] PROJECT_OVERVIEW.md - This file
- [x] .env.example - Environment template

### 9.2. Current Status

**Overall Progress:** ~60% Complete

| Component | Status | Completion |
|-----------|--------|------------|
| Infrastructure | ✅ Done | 100% |
| API Gateway | ✅ Done | 100% |
| AuthService | ✅ Done | 100% |
| UserService | ✅ Done | 100% |
| Security | ✅ Done | 100% |
| Validation | ✅ Done | 100% |
| Error Handling | ✅ Done | 100% |
| Documentation | ✅ Done | 100% |
| Testing | 🚧 Partial | 30% |
| Monitoring | ⏳ Planned | 0% |
| Frontend | ⏳ Planned | 0% |
| Additional Services | ⏳ Planned | 0% |

**Legend:**
- ✅ Done - Feature completed and tested
- 🚧 Partial - Work in progress
- ⏳ Planned - Not started yet

---

## 10. What's Next

### 10.1. Short Term (Next Sprint)

#### Testing
- [ ] Unit tests cho AuthService
- [ ] Unit tests cho UserService
- [ ] Integration tests cho API Gateway
- [ ] E2E tests cho authentication flow
- [ ] Load testing cho rate limiting

#### Monitoring & Observability
- [ ] Prometheus metrics
- [ ] Grafana dashboards
- [ ] Distributed tracing (Zipkin/Jaeger)
- [ ] Centralized logging (ELK Stack)
- [ ] Alert rules

### 10.2. Medium Term (Future Services)

#### OrderService
- [ ] Order creation and management
- [ ] Order status tracking
- [ ] Integration với UserService
- [ ] Kafka events (OrderCreated, OrderUpdated)

#### PaymentService
- [ ] Payment processing
- [ ] Payment gateway integration
- [ ] Refund handling
- [ ] Payment history

#### NotificationService
- [ ] Email notifications
- [ ] SMS notifications
- [ ] Push notifications
- [ ] Notification templates

#### ProductService
- [ ] Product catalog
- [ ] Inventory management
- [ ] Product search
- [ ] Categories and tags

### 10.3. Long Term (Future Enhancements)

#### Advanced Features
- [ ] API versioning
- [ ] GraphQL gateway
- [ ] WebSocket support (real-time updates)
- [ ] File upload service
- [ ] CDN integration
- [ ] Multi-language support (i18n)

#### Security Enhancements
- [ ] OAuth2 integration (Google, Facebook)
- [ ] Two-factor authentication (2FA)
- [ ] API key management
- [ ] IP whitelist/blacklist
- [ ] DDoS protection

#### Performance
- [ ] Redis caching layer
- [ ] Database query optimization
- [ ] CDN for static assets
- [ ] Database read replicas
- [ ] Message queue optimization

#### DevOps
- [ ] CI/CD pipeline (Jenkins/GitHub Actions)
- [ ] Kubernetes deployment
- [ ] Auto-scaling rules
- [ ] Blue-green deployment
- [ ] Disaster recovery plan

---

## 11. Documentation

### 11.1. Available Documentation

| Document | Purpose | Audience |
|----------|---------|----------|
| **README.md** | Project overview, quick start | All developers |
| **PROJECT_OVERVIEW.md** | Comprehensive project status | New developers, agents |
| **ADDING_NEW_SERVICE.md** | Guide to add new microservice | Backend developers |
| **COMMON_ERRORS_TROUBLESHOOTING.md** | Error reference and solutions | All developers |
| **.env.example** | Environment variables template | DevOps, developers |

### 11.2. Code Documentation

**All services include:**
- JavaDoc comments cho public methods
- Inline comments cho complex logic
- README trong mỗi service directory (future)
- API documentation với Swagger/OpenAPI (future)

### 11.3. Architecture Decisions

**Key Architectural Choices:**

1. **Microservices over Monolith**
   - Reason: Scalability, independent deployment, technology flexibility
   - Trade-off: Increased complexity, distributed system challenges

2. **JWT over Session-based Auth**
   - Reason: Stateless, scalable, cross-service authentication
   - Trade-off: Token size, revocation complexity

3. **Event-Driven Communication (Kafka)**
   - Reason: Loose coupling, async processing, event sourcing ready
   - Trade-off: Eventual consistency, debugging complexity

4. **API Gateway Pattern**
   - Reason: Single entry point, centralized security, simplified frontend
   - Trade-off: Single point of failure, potential bottleneck

5. **MongoDB over Relational DB**
   - Reason: Schema flexibility, horizontal scaling, document model
   - Trade-off: No ACID transactions across collections (before v4.0), eventual consistency

6. **Consul over Eureka**
   - Reason: Better health checks, key-value store, multi-DC support
   - Trade-off: Additional operational complexity

---

## 12. Environment Configuration

### 12.1. Required Environment Variables

```env
# JWT Configuration
JWT_SECRET=BASE64:your-base64-encoded-secret-here

# MongoDB URIs
AUTH_MONGODB_URI=mongodb://username:password@mongodb:27017/auth_db?authSource=admin
USER_MONGODB_URI=mongodb://username:password@mongodb:27017/user_db?authSource=admin

# Consul
CONSUL_HOST=consul
CONSUL_PORT=8500

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

### 12.2. Port Allocation

| Service | Internal Port | External Port | Notes |
|---------|---------------|---------------|-------|
| API Gateway | 8080 | 8080 | ✅ Exposed - Frontend access |
| AuthService | 8081 | ❌ Not exposed | Internal only |
| UserService | 8082 | ❌ Not exposed | Internal only |
| MongoDB | 27017 | 27017 | ✅ Exposed - Development only |
| Kafka | 29092 (internal) | 9092 (external) | ✅ Exposed - Development only |
| Consul | 8500 | 8500 | ✅ Exposed - UI access |
| Zookeeper | 2181 | 2181 | ✅ Exposed - Development only |

**Production Note:** MongoDB, Kafka, Zookeeper ports should NOT be exposed in production.

---

## 13. Development Workflow

### 13.1. Local Development Setup

```bash
# 1. Clone repository
git clone <repo-url>
cd GoMirai

# 2. Create .env file
cp .env.example .env
# Edit .env and set JWT_SECRET, MongoDB URIs

# 3. Start all services
docker-compose up -d

# 4. Verify services
docker-compose ps
curl http://localhost:8500/v1/catalog/services  # Check Consul

# 5. Test authentication
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber":"0123456789","password":"SecurePass123"}'
```

### 13.2. Development Guidelines

**Coding Standards:**
- Use Lombok to reduce boilerplate
- Always use `@Valid` for request DTOs
- Implement SecurityUtils for authorization
- Add comprehensive logging (INFO for business, ERROR for exceptions)
- Handle all exceptions in GlobalExceptionHandler
- Never log sensitive data (passwords, tokens)

**Git Workflow:**
- Feature branches: `feature/order-service`
- Bugfix branches: `bugfix/fix-jwt-validation`
- Always create PR for review
- Squash commits before merge

**Testing:**
- Write unit tests for business logic
- Integration tests for API endpoints
- Test authentication and authorization
- Verify error responses

### 13.3. Adding New Service

**Follow this checklist:**
1. ✅ Read `ADDING_NEW_SERVICE.md`
2. ✅ Create service from Spring Initializr
3. ✅ Implement security (JWT, SecurityUtils, SecurityConfig)
4. ✅ Implement validation and error handling
5. ✅ Add Kafka integration (if needed)
6. ✅ Create Dockerfile (multi-stage build)
7. ✅ Update docker-compose.yml (no exposed ports)
8. ✅ Update API Gateway whitelist
9. ✅ Update .env and .env.example
10. ✅ Test thoroughly
11. ✅ Update documentation

---

## 14. Known Limitations & Technical Debt

### 14.1. Current Limitations

1. **No Distributed Transaction Support**
   - Issue: Kafka events are fire-and-forget
   - Impact: Potential data inconsistency if consumer fails
   - Mitigation: Kafka retry mechanism, idempotent consumers
   - Future: Implement Saga pattern or 2PC

2. **JWT Token Revocation**
   - Issue: Cannot revoke JWTs before expiration
   - Impact: Compromised tokens valid until expiry
   - Mitigation: Short expiration time (24h)
   - Future: Implement token blacklist with Redis

3. **No Rate Limiting on Gateway**
   - Issue: Only AuthService has rate limiting
   - Impact: Potential DDoS on other services
   - Mitigation: Services are internal, only Gateway exposed
   - Future: Implement gateway-level rate limiting

4. **Single Database per Service**
   - Issue: No read replicas, no sharding
   - Impact: Scalability limits, single point of failure
   - Future: MongoDB replica sets, sharding

5. **No API Versioning**
   - Issue: Breaking changes affect all clients
   - Impact: Difficult to evolve APIs
   - Future: Implement URL versioning (`/v1/`, `/v2/`)

### 14.2. Technical Debt

- [ ] Add comprehensive unit tests (coverage < 50%)
- [ ] Add integration tests
- [ ] Implement health checks beyond basic UP/DOWN
- [ ] Add metrics and monitoring
- [ ] Implement distributed tracing
- [ ] Add API documentation (Swagger/OpenAPI)
- [ ] Implement centralized logging
- [ ] Add CI/CD pipeline
- [ ] Secrets management (Vault)
- [ ] Database migrations (Liquibase/Flyway for future SQL DBs)

---

## 15. Troubleshooting Quick Reference

### 15.1. Service Won't Start

```bash
# Check logs
docker-compose logs -f service-name

# Common causes:
# - Missing JWT_SECRET in .env
# - Wrong MongoDB URI
# - Port conflict
# - Kafka/Consul not ready
```

### 15.2. Authentication Fails

```bash
# Verify JWT_SECRET is same across all services
docker-compose exec auth-service env | grep JWT_SECRET
docker-compose exec user-service env | grep JWT_SECRET

# Should be identical!
```

### 15.3. Service Not Found (502)

```bash
# Check Consul registration
curl http://localhost:8500/v1/catalog/services

# Verify service health
curl http://localhost:8083/actuator/health  # Direct service call
```

### 15.4. Kafka Consumer Not Receiving

```bash
# Check consumer group
docker exec -it kafka bash
kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group user-service-group

# Verify trusted packages in application.properties
```

**For detailed troubleshooting, see:** `COMMON_ERRORS_TROUBLESHOOTING.md`

---

## 16. Contact & Support

### 16.1. For Developers

- **Architecture Questions:** Refer to this document
- **Adding New Service:** See `ADDING_NEW_SERVICE.md`
- **Troubleshooting:** See `COMMON_ERRORS_TROUBLESHOOTING.md`
- **Bug Reports:** Create GitHub issue with logs and steps to reproduce

### 16.2. For AI Agents

**When analyzing this project:**

1. **Start here:** Read this PROJECT_OVERVIEW.md first
2. **Understand architecture:** Check section 2 (System Architecture)
3. **Review current services:** Check section 3 (Current Services)
4. **Check security:** Review section 4 (Security Implementation)
5. **Adding features:** Follow ADDING_NEW_SERVICE.md
6. **Debugging:** Use COMMON_ERRORS_TROUBLESHOOTING.md

**Key Context:**
- All backend services are JWT-authenticated
- Services communicate via Kafka events
- Frontend accesses ONLY via API Gateway (port 8080)
- No direct access to backend services
- Security is the top priority

---

## 17. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-23 | Initial project overview document |

---

## 18. Summary & Quick Start

### 18.1. TL;DR

**GoMirai** là một microservices system với:
- ✅ **3 services:** API Gateway, AuthService, UserService
- ✅ **Security:** JWT authentication, authorization, rate limiting
- ✅ **Event-Driven:** Kafka-based messaging
- ✅ **Service Discovery:** Consul
- ✅ **Docker Ready:** Containerized và orchestrated với Docker Compose

**Access Points:**
- Frontend → `http://localhost:8080` (API Gateway)
- Consul UI → `http://localhost:8500`
- Backend services → Internal only, không exposed

### 18.2. Quick Commands

```bash
# Start everything
docker-compose up -d

# Stop everything
docker-compose down

# View logs
docker-compose logs -f

# Rebuild service
docker-compose build service-name
docker-compose up -d service-name

# Check service health
curl http://localhost:8080/actuator/health

# List Consul services
curl http://localhost:8500/v1/catalog/services | jq
```

---

**🎉 End of Document**

Tài liệu này cung cấp comprehensive overview của GoMirai project. Để biết thêm chi tiết, xem các documents khác trong thư mục `docs/`.

**Happy Coding! 🚀**


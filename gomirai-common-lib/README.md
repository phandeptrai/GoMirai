# GoMirai Common Library

**Version:** 1.0.0  
**Purpose:** Shared components for all GoMirai microservices

---

## 📦 What's Included?

### 1. **Security** (`com.gomirai.common.security`)
- `JwtService` - JWT token generation and validation
- `JwtAuthenticationFilter` - Spring Security filter for JWT
- `JwtAuthenticationEntryPoint` - 401 error handler
- `SecurityUtils` - Helper methods for user authentication

### 2. **DTOs** (`com.gomirai.common.dto`)
- `response/ErrorResponse` - Standardized error response
- `response/ValidationErrorResponse` - Validation error response
- `response/ApiResponse<T>` - Generic API response wrapper
- `event/UserRegisteredEvent` - Kafka event for user registration
- `event/BaseEvent` - Base class for all events

### 3. **Enums** (`com.gomirai.common.enums`)
- `Role` - User roles (CUSTOMER, DRIVER, ADMIN)
- `AuthProvider` - Auth providers (LOCAL, GOOGLE, FACEBOOK, APPLE)
- `ServiceName` - Service names for discovery

### 4. **Exceptions** (`com.gomirai.common.exception`)
- `BusinessException` - Business logic errors
- `UnauthorizedException` - 401 errors
- `ForbiddenException` - 403 errors
- `NotFoundException` - 404 errors
- `GlobalExceptionHandler` - Centralized exception handling

### 5. **Utils** (`com.gomirai.common.util`)
- `DateTimeUtil` - Date/time formatting and conversion
- `ValidationUtil` - Input validation (phone, email, password)
- `StringUtil` - String manipulation

### 6. **Constants** (`com.gomirai.common.constant`)
- `ValidationConstants` - Validation rules and messages
- `SystemConstants` - System-wide constants

---

## 🚀 Installation

### Step 1: Build and Install Common Library

```bash
cd gomirai-common-lib
mvn clean install
```

This will install `gomirai-common-lib-1.0.0.jar` to your local Maven repository.

---

## 📖 Usage in Services

### Step 1: Add Dependency

Add this to your service's `pom.xml`:

```xml
<dependency>
    <groupId>com.gomirai</groupId>
    <artifactId>gomirai-common-lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Remove Duplicate Code

Delete these files from your service (they're now in common lib):
- `security/JwtService.java`
- `security/JwtAuthenticationFilter.java`
- `security/JwtAuthenticationEntryPoint.java`
- `dto/ErrorResponse.java`
- `dto/ValidationErrorResponse.java`
- `events/UserRegisteredEvent.java`
- `model/Role.java`
- `model/AuthProvider.java`

### Step 3: Update Imports

Change imports from service-specific to common lib:

```java
// ❌ Old
import com.gomirai.auth.security.JwtService;
import com.gomirai.auth.dto.ErrorResponse;
import com.gomirai.auth.model.Role;

// ✅ New
import com.gomirai.common.security.JwtService;
import com.gomirai.common.dto.response.ErrorResponse;
import com.gomirai.common.enums.Role;
```

### Step 4: Update SecurityConfig (if needed)

SecurityConfig should remain mostly the same. Just update imports:

```java
import com.gomirai.common.security.JwtAuthenticationFilter;
import com.gomirai.common.security.JwtAuthenticationEntryPoint;
```

---

## 💡 Examples

### Using JwtService

```java
import com.gomirai.common.security.JwtService;

@Service
public class AuthService {
    
    private final JwtService jwtService;
    
    public String generateToken(UUID userId, String role) {
        return jwtService.generateToken(userId, role);
    }
    
    public boolean validateToken(String token) {
        return jwtService.validateToken(token);
    }
}
```

### Using ErrorResponse

```java
import com.gomirai.common.dto.response.ErrorResponse;

@RestControllerAdvice
public class MyExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleError(Exception e) {
        ErrorResponse error = new ErrorResponse(
            500,
            "Internal Server Error",
            e.getMessage()
        );
        return ResponseEntity.status(500).body(error);
    }
}
```

### Using SecurityUtils

```java
import com.gomirai.common.security.SecurityUtils;

@Service
public class UserService {
    
    private final SecurityUtils securityUtils;
    
    public UserProfile getProfile(UUID userId) {
        // Check if user owns this profile or is admin
        securityUtils.validateOwnershipOrAdmin(userId);
        
        // ... get profile
    }
}
```

### Using Role Enum

```java
import com.gomirai.common.enums.Role;

@Service
public class AuthService {
    
    public void register(RegisterRequest request) {
        AuthUser user = new AuthUser();
        user.setRole(Role.CUSTOMER);  // ✅ Use enum
        
        if (user.getRole().isCustomer()) {
            // ... customer-specific logic
        }
    }
}
```

### Using ValidationUtil

```java
import com.gomirai.common.util.ValidationUtil;

@Service
public class AuthService {
    
    public void validateInput(RegisterRequest request) {
        if (!ValidationUtil.isValidPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Invalid phone number");
        }
        
        if (!ValidationUtil.isValidPassword(request.getPassword())) {
            throw new BusinessException("Password too weak");
        }
    }
}
```

---

## 📝 Versioning

This library follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version (1.x.x): Breaking changes
- **MINOR** version (x.1.x): New features (backward compatible)
- **PATCH** version (x.x.1): Bug fixes

### Current Version: 1.0.0

**Changelog:**
- Initial release with JWT, DTOs, Enums, Exceptions, Utils, Constants

---

## 🔄 Updating Common Library

When common library is updated:

1. **Update version in `pom.xml`**:
   ```xml
   <version>1.0.1</version>
   ```

2. **Build and install**:
   ```bash
   mvn clean install
   ```

3. **Update services**:
   ```xml
   <dependency>
       <groupId>com.gomirai</groupId>
       <artifactId>gomirai-common-lib</artifactId>
       <version>1.0.1</version>  <!-- Update version -->
   </dependency>
   ```

4. **Rebuild services**:
   ```bash
   mvn clean install
   ```

---

## 📚 Related Documentation

- [COMMON_LIBRARY_ANALYSIS.md](../docs/COMMON_LIBRARY_ANALYSIS.md) - Analysis and decision rationale
- [ADDING_NEW_SERVICE.md](../docs/ADDING_NEW_SERVICE.md) - Guide for adding new services
- [PROJECT_OVERVIEW.md](../docs/PROJECT_OVERVIEW.md) - Project architecture overview

---

## 🤝 Contributing

When adding new components to common lib:

1. **Ask yourself**: Is this TRULY common across multiple services?
2. **Avoid service-specific logic**
3. **Write unit tests** (80%+ coverage)
4. **Document with Javadocs**
5. **Update README**
6. **Version appropriately**

---

## ⚠️ Important Notes

1. **Common lib should NOT depend on any service** (avoid circular dependencies)
2. **Keep it focused** - Only truly shared code
3. **Test thoroughly** - Bugs here affect ALL services
4. **Version carefully** - Breaking changes impact all services
5. **Document well** - Make it easy for others to use

---

**Maintained by:** GoMirai Development Team  
**Last Updated:** 2025-11-24



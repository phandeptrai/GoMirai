package com.gomirai.identity.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import com.gomirai.identity.dto.AuthResponse;
import com.gomirai.identity.dto.GoogleAuthRequest;
import com.gomirai.identity.dto.LoginRequest;
import com.gomirai.identity.dto.RegisterRequest;
import com.gomirai.identity.dto.TokenValidationResponse;
import com.gomirai.identity.service.AuthApplicationService;

/**
 * Controller xử lý xác thực người dùng.
 * 
 * Hỗ trợ 2 phương thức đăng nhập:
 * 1. LOCAL: Đăng ký/đăng nhập bằng số điện thoại + mật khẩu
 * 2. Google OAuth: Đăng nhập bằng tài khoản Google
 * 
 * Lưu ý về Google OAuth:
 * - Chỉ thực hiện XÁC THỰC (authentication), không phân biệt đăng ký/đăng nhập
 * - Nếu tài khoản Google đã tồn tại → đăng nhập
 * - Nếu chưa tồn tại → tự động tạo tài khoản mới
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    /**
     * Đăng ký tài khoản mới bằng số điện thoại và mật khẩu.
     * 
     * @param request Thông tin đăng ký (phoneNumber, password)
     * @return AuthResponse chứa userId, role và JWT token
     */
    @PostMapping("/register")
    @Bulkhead(name = "identityAuth", fallbackMethod = "registerBulkheadFallback")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @SuppressWarnings("unused")
    public ResponseEntity<AuthResponse> registerBulkheadFallback(RegisterRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * Đăng nhập bằng số điện thoại và mật khẩu.
     * 
     * @param request Thông tin đăng nhập (phoneNumber, password)
     * @return AuthResponse chứa userId, role và JWT token
     */
    @PostMapping("/login")
    @Bulkhead(name = "identityAuth", fallbackMethod = "loginBulkheadFallback")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = authService.login(request);
        return ResponseEntity.ok(resp);
    }

    @SuppressWarnings("unused")
    public ResponseEntity<AuthResponse> loginBulkheadFallback(LoginRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * Đăng nhập/đăng ký bằng Google OAuth.
     * 
     * Luồng xử lý:
     * 1. Client gửi Google ID Token (lấy từ Google Sign-In SDK)
     * 2. Server xác thực token với Google
     * 3. Nếu user đã tồn tại → đăng nhập
     * 4. Nếu user chưa tồn tại → tự động tạo tài khoản
     * 5. Trả về JWT token của hệ thống
     * 
     * @param request Chứa Google ID Token
     * @return AuthResponse chứa userId, role và JWT token
     */
    @PostMapping("/google")
    @Bulkhead(name = "identityAuth", fallbackMethod = "googleBulkheadFallback")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse resp = authService.authenticateWithGoogle(request.getIdToken());
        return ResponseEntity.ok(resp);
    }

    @SuppressWarnings("unused")
    public ResponseEntity<AuthResponse> googleBulkheadFallback(GoogleAuthRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * Xác thực JWT token.
     * Dùng bởi API Gateway để validate token trước khi forward request.
     * 
     * @param authorization Header Authorization chứa Bearer token
     * @return TokenValidationResponse cho biết token hợp lệ hay không
     */
    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        // Trích xuất token từ header "Bearer <token>"
        String token = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : null;

        // Nếu không có token → trả về invalid
        TokenValidationResponse result = (token == null)
                ? new TokenValidationResponse(false, null, null)
                : authService.validate(token);
        return ResponseEntity.ok(result);
    }

    /**
     * Cập nhật role của user thành DRIVER.
     * Được gọi bởi UserService khi đơn đăng ký tài xế được duyệt.
     * 
     * @param userId ID của user cần cập nhật role
     */
    @PutMapping("/users/{userId}/role/driver")
    public ResponseEntity<Void> updateUserRoleToDriver(@PathVariable UUID userId) {
        authService.updateUserRoleToDriver(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Làm mới token để lấy role hiện tại từ database.
     * Dùng khi role của user đã được cập nhật (VD: CUSTOMER → DRIVER)
     * và cần token mới phản ánh role mới.
     * 
     * @param authorization Header Authorization chứa Bearer token cũ
     * @return AuthResponse chứa token mới với role cập nhật
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestHeader(name = "Authorization", required = true) String authorization) {
        // Trích xuất token (bỏ prefix "Bearer " nếu có)
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        AuthResponse newAuth = authService.refreshToken(token);
        return ResponseEntity.ok(newAuth);
    }
}

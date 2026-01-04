package com.gomirai.auth.controller;

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

import com.gomirai.auth.dto.AuthResponse;
import com.gomirai.auth.dto.GoogleAuthRequest;
import com.gomirai.auth.dto.LoginRequest;
import com.gomirai.auth.dto.RegisterRequest;
import com.gomirai.auth.dto.TokenValidationResponse;
import com.gomirai.auth.service.AuthApplicationService;

/**
 * Authentication Controller handling:
 * 1. LOCAL auth: /register, /login with phone + password
 * 2. Google OAuth: /google with ID token
 * 
 * Note: Google OAuth only performs AUTHENTICATION (identity verification).
 * No distinction between "login" and "register" from user's perspective.
 * - If providerUserId exists → system treats it as login
 * - If not exists → system performs auto-registration
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthApplicationService authService;

    public AuthController(AuthApplicationService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse resp = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse resp = authService.login(request);
        return ResponseEntity.ok(resp);
    }

    /**
     * Google OAuth Authentication endpoint.
     * 
     * Accepts a Google ID token from the client (obtained via Google Sign-In SDK),
     * verifies it with Google, and returns a system JWT token.
     * 
     * This endpoint handles both:
     * - Login: if user with this Google account already exists
     * - Auto-registration: if this is a new Google account
     * 
     * @param request GoogleAuthRequest containing the Google ID token
     * @return AuthResponse with system JWT token, userId, and role
     */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> authenticateWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse resp = authService.authenticateWithGoogle(request.getIdToken());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        String token = (authorization != null && authorization.startsWith("Bearer ")) ? authorization.substring(7)
                : null;
        TokenValidationResponse result = (token == null)
                ? new TokenValidationResponse(false, null, null)
                : authService.validate(token);
        return ResponseEntity.ok(result);
    }

    /**
     * Update user role to DRIVER
     * Called by UserService when driver application is approved
     */
    @PutMapping("/users/{userId}/role/driver")
    public ResponseEntity<Void> updateUserRoleToDriver(@PathVariable UUID userId) {
        authService.updateUserRoleToDriver(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * Refresh token to get current role
     * Used when user role has been updated and needs new token
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestHeader(name = "Authorization", required = true) String authorization) {
        String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
        AuthResponse newAuth = authService.refreshToken(token);
        return ResponseEntity.ok(newAuth);
    }
}

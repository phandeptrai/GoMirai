package com.gomirai.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gomirai.auth.dto.AuthResponse;
import com.gomirai.auth.dto.GoogleUserInfo;
import com.gomirai.auth.dto.LoginRequest;
import com.gomirai.auth.dto.RegisterRequest;
import com.gomirai.auth.dto.TokenValidationResponse;
import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.auth.messaging.UserEventsProducer;
import com.gomirai.common.enums.AuthProvider;
import com.gomirai.auth.model.AuthUser;
import com.gomirai.common.enums.Role;
import com.gomirai.auth.repository.AuthUserRepository;
import com.gomirai.common.security.JwtService;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

/**
 * Main authentication service handling:
 * 1. LOCAL auth: phone + password registration/login
 * 2. Google OAuth: ID token verification and auto-registration
 */
@Service
@Slf4j
public class AuthApplicationService {

    private final AuthUserRepository authUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserEventsProducer eventsProducer;
    private final GoogleOAuthService googleOAuthService;

    public AuthApplicationService(AuthUserRepository authUserRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            UserEventsProducer eventsProducer,
            GoogleOAuthService googleOAuthService) {
        this.authUserRepository = authUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.eventsProducer = eventsProducer;
        this.googleOAuthService = googleOAuthService;
    }

    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra trùng số điện thoại TRƯỚC khi bắt đầu transaction
        if (authUserRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new BusinessException("Số điện thoại đã tồn tại");
        }

        // Bắt đầu transaction chỉ khi cần save vào DB
        return registerInternal(request);
    }

    @Transactional
    private AuthResponse registerInternal(RegisterRequest request) {
        AuthUser user = new AuthUser();
        user.setUserId(UUID.randomUUID());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.CUSTOMER);
        AuthUser saved = authUserRepository.save(user);

        eventsProducer.sendUserRegistered(
                new UserRegisteredEvent(saved.getUserId(), saved.getPhoneNumber(), saved.getRole().name()));

        String token = jwtService.generateToken(saved.getUserId(), saved.getRole().name());
        return new AuthResponse(saved.getUserId(), saved.getRole().name(), token);
    }

    public AuthResponse login(LoginRequest request) {
        AuthUser user = authUserRepository.findByPhoneNumber(request.getPhoneNumber())
                .orElseThrow(() -> new BusinessException("Thông tin đăng nhập không hợp lệ"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Thông tin đăng nhập không hợp lệ");
        }
        String token = jwtService.generateToken(user.getUserId(), user.getRole().name());
        return new AuthResponse(user.getUserId(), user.getRole().name(), token);
    }

    /**
     * Authenticate with Google OAuth.
     * 
     * Google OAuth performs AUTHENTICATION only - no distinction between
     * "login" and "register" from the user's perspective.
     * 
     * Flow:
     * 1. Verify Google ID token
     * 2. Check if user exists (by providerUserId)
     * - If exists: treat as LOGIN, return JWT token
     * - If not exists: AUTO-REGISTRATION
     * - Create new AuthUser with Google info
     * - Emit UserRegisteredEvent to UserService & PaymentService
     * - Return JWT token
     * 
     * Note: OAuth users may not have phone number initially.
     * They can add phone number later via profile update.
     * 
     * @param idToken Google ID token from client
     * @return AuthResponse with JWT token and user info
     */
    public AuthResponse authenticateWithGoogle(String idToken) {
        log.info("Processing Google OAuth authentication");

        // Step 1: Verify token and get user info from Google
        GoogleUserInfo googleUserInfo = googleOAuthService.verifyIdToken(idToken);
        String providerUserId = googleUserInfo.getSub();

        log.info("Google token verified for user: {} (sub: {})",
                googleUserInfo.getEmail(), providerUserId);

        // Step 2: Check if user already exists with this Google account
        Optional<AuthUser> existingUser = authUserRepository.findByProviderAndProviderUserId(
                AuthProvider.GOOGLE, providerUserId);

        if (existingUser.isPresent()) {
            // User exists - this is a LOGIN
            log.info("Existing Google user found, performing login for userId: {}",
                    existingUser.get().getUserId());
            return loginGoogleUser(existingUser.get(), googleUserInfo);
        } else {
            // User doesn't exist - this is AUTO-REGISTRATION
            log.info("New Google user, performing auto-registration for email: {}",
                    googleUserInfo.getEmail());
            return registerGoogleUser(googleUserInfo);
        }
    }

    /**
     * Login existing Google OAuth user.
     * Updates user info from Google in case it changed (name, picture).
     */
    @Transactional
    private AuthResponse loginGoogleUser(AuthUser user, GoogleUserInfo googleUserInfo) {
        // Update user info from Google (may have changed)
        boolean updated = false;

        if (googleUserInfo.getName() != null && !googleUserInfo.getName().equals(user.getFullName())) {
            user.setFullName(googleUserInfo.getName());
            updated = true;
        }
        if (googleUserInfo.getPicture() != null && !googleUserInfo.getPicture().equals(user.getProfilePictureUrl())) {
            user.setProfilePictureUrl(googleUserInfo.getPicture());
            updated = true;
        }
        if (googleUserInfo.getEmail() != null && !googleUserInfo.getEmail().equals(user.getEmail())) {
            user.setEmail(googleUserInfo.getEmail());
            updated = true;
        }

        if (updated) {
            authUserRepository.save(user);
            log.info("Updated Google user info for userId: {}", user.getUserId());
        }

        String token = jwtService.generateToken(user.getUserId(), user.getRole().name());
        log.info("Google login successful for userId: {}", user.getUserId());
        return new AuthResponse(user.getUserId(), user.getRole().name(), token);
    }

    /**
     * Auto-register new Google OAuth user.
     * Creates AuthUser and emits UserRegisteredEvent for UserService &
     * PaymentService.
     * 
     * Note: OAuth users don't have phone number initially.
     * The UserRegisteredEvent includes email instead.
     */
    @Transactional
    private AuthResponse registerGoogleUser(GoogleUserInfo googleUserInfo) {
        // Create new AuthUser for Google OAuth
        AuthUser user = new AuthUser();
        user.setUserId(UUID.randomUUID());
        user.setProvider(AuthProvider.GOOGLE);
        user.setProviderUserId(googleUserInfo.getSub());
        user.setEmail(googleUserInfo.getEmail());
        user.setFullName(googleUserInfo.getName());
        user.setProfilePictureUrl(googleUserInfo.getPicture());
        user.setRole(Role.CUSTOMER);
        // phoneNumber and passwordHash are null for OAuth users

        AuthUser saved = authUserRepository.save(user);
        log.info("Created new Google OAuth user with userId: {}", saved.getUserId());

        // Emit UserRegisteredEvent for UserService and PaymentService
        // Using the extended constructor with email and fullName for OAuth users
        UserRegisteredEvent event = new UserRegisteredEvent(
                saved.getUserId(),
                null, // phoneNumber is null for OAuth users
                saved.getRole().name(),
                saved.getEmail(),
                saved.getFullName(),
                AuthProvider.GOOGLE.name());
        eventsProducer.sendUserRegistered(event);
        log.info("Emitted UserRegisteredEvent for Google OAuth user: {}", saved.getUserId());

        String token = jwtService.generateToken(saved.getUserId(), saved.getRole().name());
        log.info("Google auto-registration successful for userId: {}", saved.getUserId());
        return new AuthResponse(saved.getUserId(), saved.getRole().name(), token);
    }

    public TokenValidationResponse validate(String token) {
        Optional<Claims> claims = jwtService.parseToken(token);
        if (claims.isEmpty()) {
            return new TokenValidationResponse(false, null, null);
        }
        Claims c = claims.get();
        UUID userId = UUID.fromString(c.getSubject());
        String role = c.get("role", String.class);
        return new TokenValidationResponse(true, userId, role);
    }

    /**
     * Update user role to DRIVER
     * Called when driver application is approved by admin
     */
    @Transactional
    public void updateUserRoleToDriver(UUID userId) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found: " + userId));

        if (user.getRole() == Role.DRIVER) {
            // Already a driver, no need to update
            return;
        }

        user.setRole(Role.DRIVER);
        authUserRepository.save(user);

        // Note: User needs to login again to get new JWT token with DRIVER role
    }

    /**
     * Refresh token to get current role from database
     * Used when user role has been updated
     */
    public AuthResponse refreshToken(String oldToken) {
        Optional<Claims> claims = jwtService.parseToken(oldToken);
        if (claims.isEmpty()) {
            throw new BusinessException("Invalid token");
        }

        UUID userId = UUID.fromString(claims.get().getSubject());

        // Get current role from database (may have been updated)
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Generate new token with current role
        String newToken = jwtService.generateToken(user.getUserId(), user.getRole().name());
        return new AuthResponse(user.getUserId(), user.getRole().name(), newToken);
    }
}

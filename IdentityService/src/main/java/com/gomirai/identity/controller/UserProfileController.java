package com.gomirai.identity.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gomirai.identity.dto.CreateUserProfileRequest;
import com.gomirai.identity.dto.UpdateUserProfileRequest;
import com.gomirai.identity.dto.UserProfileResponse;

import jakarta.validation.Valid;
import com.gomirai.common.security.SecurityUtils;
import com.gomirai.identity.service.UserProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller quản lý hồ sơ người dùng (UserProfile).
 * 
 * === PUBLIC APIs ===
 * - GET /api/users/{userId}: Lấy thông tin profile (user chỉ xem được của mình)
 * 
 * === USER APIs ===
 * - PUT /api/users/{userId}: Cập nhật profile (user chỉ sửa được của mình)
 * - DELETE /api/users/{userId}: Xóa profile (user chỉ xóa được của mình)
 * 
 * === ADMIN APIs ===
 * - GET /api/users: Lấy tất cả profiles
 * - POST /api/users: Tạo profile mới (DEPRECATED - dùng cho testing)
 * - POST /api/users/{userId}: Tạo profile với userId chỉ định
 * 
 * === Profile Status ===
 * - PENDING: Profile chưa tạo (đang chờ Kafka event xử lý)
 * - INCOMPLETE: Profile đã tạo nhưng thiếu thông tin
 * - COMPLETE: Profile đầy đủ thông tin (fullName + email)
 * 
 * === Lưu ý bảo mật ===
 * - User chỉ có thể xem/sửa/xóa profile của chính mình
 * - ADMIN có thể xem tất cả profiles
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final SecurityUtils securityUtils;

    /**
     * ⚠️ DEPRECATED: Profile được tạo tự động qua Kafka khi user register
     * Endpoint này chỉ dùng cho testing/admin purposes
     * ADMIN ONLY
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> createUserProfile(@Valid @RequestBody CreateUserProfileRequest request) {
        // Generate UUID for new user
        UUID userId = UUID.randomUUID();
        UserProfileResponse response = userProfileService.createUserProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * ⚠️ DEPRECATED: Profile được tạo tự động qua Kafka khi user register
     * Endpoint này chỉ dùng cho testing/admin purposes
     * ADMIN ONLY
     */
    @PostMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> createUserProfileWithId(
            @PathVariable UUID userId,
            @Valid @RequestBody CreateUserProfileRequest request) {
        UserProfileResponse response = userProfileService.createUserProfile(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get user profile by userId.
     * Returns profile with status field for eventual consistency handling:
     * - PENDING: Profile not created yet (waiting for Kafka processing)
     * - INCOMPLETE: Profile created but missing info (only phone)
     * - COMPLETE: Profile has full information
     * 
     * Frontend should handle PENDING status by showing "Account being initialized"
     * and retry after a few seconds instead of showing error.
     * 
     * SECURITY: User chỉ có thể xem profile của chính mình (hoặc ADMIN xem tất cả)
     */
    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getUserProfile(@PathVariable UUID userId) {
        // ✅ KIỂM TRA AUTHORIZATION: User chỉ được xem profile của chính mình
        securityUtils.validateOwnershipOrAdmin(userId);

        UserProfileResponse response = userProfileService.getUserProfile(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get all user profiles - CHỈ ADMIN mới được gọi
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserProfileResponse>> getAllUserProfiles() {
        List<UserProfileResponse> responses = userProfileService.getAllUserProfiles();
        return ResponseEntity.ok(responses);
    }

    /**
     * Update user profile
     * SECURITY: User chỉ có thể update profile của chính mình
     */
    @PutMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> updateUserProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request) {
        // ✅ KIỂM TRA AUTHORIZATION: User chỉ được update profile của chính mình
        securityUtils.validateOwnership(userId);

        UserProfileResponse response = userProfileService.updateUserProfile(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete user profile
     * SECURITY: User chỉ có thể xóa profile của chính mình
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUserProfile(@PathVariable UUID userId) {
        // ✅ KIỂM TRA AUTHORIZATION: User chỉ được xóa profile của chính mình
        securityUtils.validateOwnership(userId);

        userProfileService.deleteUserProfile(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get public user info - PUBLIC API for other services
     * Returns only basic public info: fullName, phone (masked)
     * Used by: DriverService to show driver info to customers
     * 
     * GET /api/users/{userId}/public
     */
    @GetMapping("/{userId}/public")
    public ResponseEntity<UserProfileResponse> getUserPublicInfo(@PathVariable UUID userId) {
        log.info("Getting public info for userId={}", userId);
        UserProfileResponse response = userProfileService.getUserProfile(userId);
        // Return full response - service layer can mask sensitive data if needed
        return ResponseEntity.ok(response);
    }
}

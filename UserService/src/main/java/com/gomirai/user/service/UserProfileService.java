package com.gomirai.user.service;

/**
 * CI/CD Trigger Comment: UserService is acti
 */

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gomirai.user.dto.CreateUserProfileRequest;
import com.gomirai.user.dto.UpdateUserProfileRequest;
import com.gomirai.user.dto.UserProfileResponse;
import com.gomirai.user.model.Address;
import com.gomirai.user.model.UserProfile;
import com.gomirai.user.repository.UserProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý hồ sơ người dùng (UserProfile).
 * 
 * Các chức năng chính:
 * 1. createEmptyUserProfile: Tạo profile rỗng khi user đăng ký LOCAL (chỉ có
 * phone)
 * 2. createOAuthUserProfile: Tạo profile khi user đăng ký qua OAuth (có email,
 * fullName)
 * 3. getUserProfile: Lấy thông tin profile, trả về PENDING nếu chưa có
 * 4. updateUserProfile: Cập nhật thông tin profile
 * 
 * Profile Status:
 * - PENDING: Profile chưa được tạo (đang chờ Kafka event từ AuthService)
 * - INCOMPLETE: Profile đã tạo nhưng thiếu thông tin bắt buộc
 * - COMPLETE: Profile đầy đủ thông tin (fullName + email)
 * 
 * Luồng tạo profile:
 * 1. User đăng ký tại AuthService
 * 2. AuthService publish UserRegisteredEvent qua Kafka
 * 3. Consumer trong UserService nhận event
 * 4. Gọi createEmptyUserProfile hoặc createOAuthUserProfile
 * 5. User có thể bổ sung thông tin qua updateUserProfile
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;

    @Transactional
    public UserProfileResponse createUserProfile(UUID userId, CreateUserProfileRequest request) {
        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User profile already exists for userId: " + userId);
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setFullName(request.getFullName());
        profile.setPhone(request.getPhone());
        profile.setEmail(request.getEmail());
        profile.setAddress(request.getAddress());
        profile.setDateOfBirth(request.getDateOfBirth());

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created user profile for userId: {}", userId);
        return mapToResponse(saved);
    }

    @Transactional
    public UserProfileResponse createEmptyUserProfile(UUID userId, String phoneNumber) {
        if (userProfileRepository.existsByUserId(userId)) {
            log.warn("User profile already exists for userId: {}, skipping creation", userId);
            return getUserProfile(userId);
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setPhone(phoneNumber); // Lấy số điện thoại từ event đăng ký
        profile.setFullName(null);
        profile.setEmail(null);
        profile.setAddress(null);
        profile.setDateOfBirth(null);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created empty user profile for userId: {} with phone: {}", userId, phoneNumber);
        return mapToResponse(saved);
    }

    /**
     * Create user profile from OAuth registration event.
     * OAuth users have email and fullName but may not have phone number.
     * 
     * @param userId       User ID
     * @param email        Email from OAuth provider
     * @param fullName     Full name from OAuth provider (optional)
     * @param authProvider The OAuth provider (GOOGLE, FACEBOOK, etc.)
     */
    @Transactional
    public UserProfileResponse createOAuthUserProfile(UUID userId, String email, String fullName, String authProvider) {
        if (userProfileRepository.existsByUserId(userId)) {
            log.warn("User profile already exists for userId: {}, skipping creation", userId);
            return getUserProfile(userId);
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setEmail(email); // Email from OAuth provider
        profile.setFullName(fullName); // Full name from OAuth provider
        profile.setPhone(null); // OAuth users may not have phone initially
        profile.setAddress(null);
        profile.setDateOfBirth(null);

        UserProfile saved = userProfileRepository.save(profile);
        log.info("Created OAuth ({}) user profile for userId: {} with email: {}",
                authProvider, userId, email);
        return mapToResponse(saved);
    }

    public UserProfileResponse getUserProfile(UUID userId) {
        return userProfileRepository.findByUserId(userId)
                .map(this::mapToResponse)
                .orElseGet(() -> createPendingProfileResponse(userId));
    }

    private UserProfileResponse createPendingProfileResponse(UUID userId) {
        log.info("Profile not found for userId: {}, returning PENDING status", userId);
        UserProfileResponse response = new UserProfileResponse();
        response.setUserId(userId);
        response.setStatus(UserProfileResponse.ProfileStatus.PENDING);
        return response;
    }

    public List<UserProfileResponse> getAllUserProfiles() {
        return userProfileRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserProfileResponse updateUserProfile(UUID userId, UpdateUserProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found for userId: " + userId));

        if (request.getFullName() != null) {
            profile.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        if (request.getAddress() != null) {
            profile.setAddress(request.getAddress());
        }
        if (request.getDateOfBirth() != null) {
            profile.setDateOfBirth(request.getDateOfBirth());
        }

        UserProfile updated = userProfileRepository.save(profile);
        log.info("Updated user profile for userId: {}", userId);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteUserProfile(UUID userId) {
        if (!userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User profile not found for userId: " + userId);
        }
        userProfileRepository.deleteById(userId);
        log.info("Deleted user profile for userId: {}", userId);
    }

    private UserProfileResponse mapToResponse(UserProfile profile) {
        UserProfileResponse.ProfileStatus status = determineProfileStatus(profile);
        return new UserProfileResponse(
                profile.getUserId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getAddress(),
                profile.getDateOfBirth(),
                status);
    }

    private UserProfileResponse.ProfileStatus determineProfileStatus(UserProfile profile) {
        boolean hasFullName = profile.getFullName() != null && !profile.getFullName().isEmpty();
        boolean hasEmail = profile.getEmail() != null && !profile.getEmail().isEmpty();

        if (hasFullName && hasEmail) {
            return UserProfileResponse.ProfileStatus.COMPLETE;
        } else {
            return UserProfileResponse.ProfileStatus.INCOMPLETE;
        }
    }
}

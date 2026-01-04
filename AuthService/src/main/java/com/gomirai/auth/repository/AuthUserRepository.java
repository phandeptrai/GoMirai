package com.gomirai.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gomirai.auth.model.AuthUser;
import com.gomirai.common.enums.AuthProvider;

public interface AuthUserRepository extends MongoRepository<AuthUser, UUID> {
    Optional<AuthUser> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find user by OAuth provider and provider-specific user ID
     * Used for Google OAuth authentication
     */
    Optional<AuthUser> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
     * Check if user exists with given OAuth provider and provider-specific user ID
     */
    boolean existsByProviderAndProviderUserId(AuthProvider provider, String providerUserId);

    /**
     * Find user by email (for OAuth users who don't have phone number)
     */
    Optional<AuthUser> findByEmail(String email);
}

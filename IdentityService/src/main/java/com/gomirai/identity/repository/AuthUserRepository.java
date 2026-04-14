package com.gomirai.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.gomirai.identity.model.AuthUser;
import com.gomirai.common.enums.AuthProvider;

public interface AuthUserRepository extends MongoRepository<AuthUser, UUID> {

    Optional<AuthUser> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    /**
     * Find user by OAuth provider and provider-specific user ID
     * Used for Google OAuth authentication
     */
    Optional<AuthUser> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}

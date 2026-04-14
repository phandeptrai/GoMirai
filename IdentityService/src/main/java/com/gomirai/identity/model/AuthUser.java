package com.gomirai.identity.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.gomirai.common.enums.AuthProvider;
import com.gomirai.common.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AuthUser entity storing authentication credentials.
 * 
 * Supports two authentication methods:
 * 1. LOCAL: phone + password
 * 2. GOOGLE OAuth: providerUserId (Google sub) + email
 * 
 * Google OAuth Logic:
 * - Google OAuth only performs AUTHENTICATION (identity verification)
 * - No distinction between "login" and "register" from user perspective
 * - If providerUserId exists → treat as login
 * - If providerUserId not exists → auto-registration, emit events to
 * UserService and PaymentService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "auth_users")
@CompoundIndexes({
        @CompoundIndex(name = "provider_userId_idx", def = "{'provider': 1, 'providerUserId': 1}", unique = true, sparse = true)
})
public class AuthUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private UUID userId;

    /**
     * Phone number for LOCAL auth, nullable for OAuth users
     */
    @Indexed(unique = true, sparse = true)
    private String phoneNumber;

    /**
     * Password hash for LOCAL auth, null for OAuth users
     */
    private String passwordHash;

    /**
     * Authentication provider (LOCAL, GOOGLE, FACEBOOK, APPLE)
     */
    private AuthProvider provider;

    /**
     * User role (CUSTOMER, DRIVER, ADMIN)
     */
    private Role role;

    /**
     * Provider-specific user ID (e.g., Google sub ID)
     * Only used for OAuth providers, null for LOCAL auth
     */
    private String providerUserId;

    /**
     * Email from OAuth provider
     * Primary contact for OAuth users (since they may not have phone)
     */
    private String email;

    /**
     * Full name from OAuth provider (optional)
     */
    private String fullName;

    /**
     * Profile picture URL from OAuth provider (optional)
     */
    private String profilePictureUrl;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

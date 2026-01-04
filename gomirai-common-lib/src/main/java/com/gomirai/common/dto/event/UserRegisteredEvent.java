package com.gomirai.common.dto.event;

import java.util.UUID;

/**
 * Kafka Event: User Registration
 * 
 * Published by: AuthService
 * Consumed by: UserService, PaymentService
 * 
 * Triggered when a new user successfully registers (LOCAL or OAuth)
 * 
 * For LOCAL auth: phoneNumber is required
 * For OAuth auth: email is required, phoneNumber may be null
 */
public record UserRegisteredEvent(
        UUID userId,
        String phoneNumber, // Required for LOCAL, nullable for OAuth
        String role,
        String email, // Required for OAuth, nullable for LOCAL
        String fullName, // Optional, from OAuth provider
        String authProvider // LOCAL, GOOGLE, FACEBOOK, APPLE
) {
    /**
     * Constructor for backwards compatibility (LOCAL auth)
     */
    public UserRegisteredEvent(UUID userId, String phoneNumber, String role) {
        this(userId, phoneNumber, role, null, null, "LOCAL");
    }

    /**
     * Validate event data
     * For OAuth users, email is sufficient (no phone required)
     */
    public void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        // Either phoneNumber OR email must be provided
        boolean hasPhone = phoneNumber != null && !phoneNumber.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        if (!hasPhone && !hasEmail) {
            throw new IllegalArgumentException("Either phoneNumber or email must be provided");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role cannot be null or empty");
        }
    }
}

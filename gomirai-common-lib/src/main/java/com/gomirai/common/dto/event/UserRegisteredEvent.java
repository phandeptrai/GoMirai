package com.gomirai.common.dto.event;

import java.util.UUID;

/**
 * Kafka Event: User Registration
 * 
 * Published by: AuthService
 * Consumed by: UserService
 * 
 * Triggered when a new user successfully registers
 */
public record UserRegisteredEvent(
    UUID userId,
    String phoneNumber,
    String role
) {
    /**
     * Validate event data
     */
    public void validate() {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("phoneNumber cannot be null or empty");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("role cannot be null or empty");
        }
    }
}



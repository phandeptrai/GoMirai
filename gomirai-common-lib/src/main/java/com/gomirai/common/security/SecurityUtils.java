package com.gomirai.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared Security Utilities for all GoMirai microservices
 * 
 * Provides helper methods for:
 * - Getting current authenticated user
 * - Checking user roles
 * - Validating ownership
 */
@Component
public class SecurityUtils {

    /**
     * Get current authenticated user ID
     * @throws SecurityException if user is not authenticated
     */
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("User is not authenticated");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }
        
        if (principal instanceof String) {
            try {
                return UUID.fromString((String) principal);
            } catch (IllegalArgumentException e) {
                // Not a UUID string, fall through to exception
            }
        }

        throw new SecurityException("Invalid authentication principal");
    }

    /**
     * Get current authenticated user ID or null if not authenticated
     */
    public UUID getCurrentUserIdOrNull() {
        try {
            return getCurrentUserId();
        } catch (SecurityException e) {
            return null;
        }
    }

    /**
     * Check if current user has specific role
     */
    public boolean hasRole(String role) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
    }

    /**
     * Check if current user is ADMIN
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /**
     * Check if current user is DRIVER
     */
    public boolean isDriver() {
        return hasRole("DRIVER");
    }

    /**
     * Check if current user is CUSTOMER
     */
    public boolean isCustomer() {
        return hasRole("CUSTOMER");
    }

    /**
     * Validate that requested user ID matches current user ID
     * @throws SecurityException if not authorized
     */
    public void validateOwnership(UUID requestedUserId) {
        UUID currentUserId = getCurrentUserId();
        if (!currentUserId.equals(requestedUserId)) {
            throw new SecurityException("You are not authorized to access this resource");
        }
    }

    /**
     * Validate ownership OR admin access
     * Admins can access any resource
     */
    public void validateOwnershipOrAdmin(UUID requestedUserId) {
        if (!isAdmin()) {
            validateOwnership(requestedUserId);
        }
    }

    /**
     * Check if user is authenticated
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}



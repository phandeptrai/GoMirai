package com.gomirai.user.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    /**
     * Lấy userId của user đang đăng nhập từ JWT token
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
        
        throw new SecurityException("Invalid authentication principal");
    }

    /**
     * Kiểm tra xem user hiện tại có phải là owner của resource không
     */
    public void validateOwnership(UUID requestedUserId) {
        UUID currentUserId = getCurrentUserId();
        if (!currentUserId.equals(requestedUserId)) {
            throw new SecurityException("You are not authorized to access this resource");
        }
    }

    /**
     * Kiểm tra xem user hiện tại có role ADMIN không
     */
    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Kiểm tra ownership hoặc admin
     */
    public void validateOwnershipOrAdmin(UUID requestedUserId) {
        if (!isAdmin()) {
            validateOwnership(requestedUserId);
        }
    }
}


package com.gomirai.common.util;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validation utility functions
 */
public class ValidationUtil {

    // Phone number: 9-11 digits
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{9,11}$");
    
    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    
    // Password: at least 8 chars, 1 lowercase, 1 uppercase, 1 digit
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$"
    );

    /**
     * Validate phone number (9-11 digits)
     */
    public static boolean isValidPhoneNumber(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * Validate email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validate password strength
     */
    public static boolean isValidPassword(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    /**
     * Validate UUID string
     */
    public static boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Validate string is not null or empty
     */
    public static boolean isNotBlank(String str) {
        return str != null && !str.isBlank();
    }

    /**
     * Validate string length
     */
    public static boolean isValidLength(String str, int minLength, int maxLength) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * Sanitize string (remove dangerous characters)
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Remove HTML tags and script tags
        return input.replaceAll("<[^>]*>", "")
                    .replaceAll("(?i)<script[^>]*>.*?</script>", "")
                    .trim();
    }
}



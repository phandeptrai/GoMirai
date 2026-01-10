package com.gomirai.common.util;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * String utility functions
 */
public class StringUtil {

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Check if string is null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if string is null or blank
     */
    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    /**
     * Check if string is not blank
     */
    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * Truncate string to max length
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }

    /**
     * Generate random alphanumeric string
     */
    public static String randomAlphanumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    /**
     * Generate random UUID string
     */
    public static String randomUUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Mask phone number (show only last 4 digits)
     * Example: 0123456789 -> ******6789
     */
    public static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 4) {
            return "****";
        }
        int visibleDigits = 4;
        int maskLength = phone.length() - visibleDigits;
        return "*".repeat(maskLength) + phone.substring(maskLength);
    }

    /**
     * Mask email (show only first 2 chars and domain)
     * Example: john@example.com -> jo***@example.com
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return "**@" + parts[1];
        }
        return parts[0].substring(0, 2) + "***@" + parts[1];
    }

    /**
     * Convert to camelCase
     */
    public static String toCamelCase(String str) {
        if (isBlank(str)) {
            return str;
        }
        String[] parts = str.split("[-_ ]");
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase());
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(parts[i].substring(0, 1).toUpperCase());
                sb.append(parts[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /**
     * Convert to snake_case
     */
    public static String toSnakeCase(String str) {
        if (isBlank(str)) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2")
                  .replaceAll("[-\\s]", "_")
                  .toLowerCase();
    }
}



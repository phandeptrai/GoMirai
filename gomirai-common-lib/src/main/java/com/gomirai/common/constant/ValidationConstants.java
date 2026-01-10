package com.gomirai.common.constant;

/**
 * Validation constants used across all GoMirai services
 */
public class ValidationConstants {

    // Regex Patterns
    public static final String PHONE_REGEX = "^[0-9]{9,11}$";
    public static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$";
    public static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    // Length Constraints
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MIN_PHONE_LENGTH = 9;
    public static final int MAX_PHONE_LENGTH = 11;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_EMAIL_LENGTH = 255;
    public static final int MAX_ADDRESS_LENGTH = 500;

    // Validation Messages
    public static final String PHONE_INVALID_MSG = "Phone number must be 9-11 digits";
    public static final String EMAIL_INVALID_MSG = "Invalid email format";
    public static final String PASSWORD_INVALID_MSG = "Password must contain at least one lowercase letter, one uppercase letter, and one digit";
    public static final String PASSWORD_LENGTH_MSG = "Password must be between 8 and 128 characters";
    public static final String REQUIRED_FIELD_MSG = " is required";
    public static final String UUID_INVALID_MSG = "Invalid UUID format";

    private ValidationConstants() {
        // Utility class, prevent instantiation
    }
}



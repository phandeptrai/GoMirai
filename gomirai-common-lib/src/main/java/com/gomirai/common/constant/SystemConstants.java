package com.gomirai.common.constant;

/**
 * System-wide constants for GoMirai project
 */
public class SystemConstants {

    // HTTP Headers
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_ACCEPT = "Accept";
    public static final String BEARER_PREFIX = "Bearer ";

    // Date/Time Formats
    public static final String ISO_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";
    public static final String SIMPLE_DATE_FORMAT = "yyyy-MM-dd";
    public static final String SIMPLE_DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // JWT
    public static final String JWT_CLAIM_ROLE = "role";
    public static final String JWT_CLAIM_USER_ID = "sub";
    public static final long JWT_DEFAULT_TTL_MS = 3600000; // 1 hour

    // Kafka Topics
    public static final String TOPIC_USER_REGISTERED = "user-registered";
    public static final String TOPIC_USER_UPDATED = "user-updated";
    public static final String TOPIC_USER_DELETED = "user-deleted";

    // CORS
    public static final String[] ALLOWED_METHODS = {"GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"};
    public static final String[] ALLOWED_HEADERS = {"*"};
    public static final long CORS_MAX_AGE = 3600L;

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_NUMBER = 0;

    // Rate Limiting
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    public static final int AUTH_RATE_LIMIT_PER_MINUTE = 5;

    // Service Names (Consul)
    public static final String SERVICE_API_GATEWAY = "api-gateway";
    public static final String SERVICE_IDENTITY = "identity-service";
    public static final String SERVICE_PAYMENT = "payment-service";
    public static final String SERVICE_RIDE = "ride-service";
    public static final String SERVICE_COMMUNICATION = "communication-service";
    public static final String SERVICE_DRIVER = "driver-service";
    public static final String SERVICE_TRACKING = "tracking-service";

    private SystemConstants() {
        // Utility class, prevent instantiation
    }
}



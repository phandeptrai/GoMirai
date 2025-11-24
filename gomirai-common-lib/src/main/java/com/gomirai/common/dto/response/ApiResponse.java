package com.gomirai.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API Response wrapper for consistent response format
 * 
 * Example:
 * {
 *   "timestamp": "2025-11-24T12:00:00.000",
 *   "success": true,
 *   "message": "User created successfully",
 *   "data": { ... }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    
    private boolean success;
    private String message;
    private T data;

    /**
     * Success response
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(LocalDateTime.now(), true, "Success", data);
    }

    /**
     * Success response with custom message
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(LocalDateTime.now(), true, message, data);
    }

    /**
     * Error response
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(LocalDateTime.now(), false, message, null);
    }
}



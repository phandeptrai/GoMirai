package com.gomirai.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized Validation Error Response for all GoMirai services
 * 
 * Example:
 * {
 *   "timestamp": "2025-11-24T12:00:00.000",
 *   "status": 400,
 *   "error": "Validation Error",
 *   "message": "Request validation failed",
 *   "errors": {
 *     "phoneNumber": "Phone number must be 9-11 digits",
 *     "password": "Password must contain at least one lowercase letter"
 *   }
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    
    private int status;
    private String error;
    private String message;
    private Map<String, String> errors;  // Field-level errors

    /**
     * Constructor with automatic timestamp
     */
    public ValidationErrorResponse(int status, String error, String message, Map<String, String> errors) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
        this.errors = errors;
    }
}



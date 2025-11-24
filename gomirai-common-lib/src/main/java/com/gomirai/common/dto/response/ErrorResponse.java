package com.gomirai.common.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Standardized Error Response for all GoMirai services
 * 
 * Example:
 * {
 *   "timestamp": "2025-11-24T12:00:00.000",
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "User not found"
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;
    
    private int status;
    private String error;
    private String message;

    /**
     * Constructor with automatic timestamp
     */
    public ErrorResponse(int status, String error, String message) {
        this.timestamp = LocalDateTime.now();
        this.status = status;
        this.error = error;
        this.message = message;
    }
}



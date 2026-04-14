package com.gomirai.ride.map.controller;

import com.gomirai.common.dto.response.ErrorResponse;
import com.gomirai.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception Handler specific to Map service
 * Returns standardized error format for all map-related errors.
 */
@RestControllerAdvice(basePackages = "com.gomirai.ride.map")
@Slf4j
public class MapExceptionHandler {

    /**
     * Handle Validation Exceptions (vibe like BookingExceptionHandler)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<com.gomirai.common.dto.response.ValidationErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Map service validation error occurred");
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        com.gomirai.common.dto.response.ValidationErrorResponse response = new com.gomirai.common.dto.response.ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Error",
            "Request validation failed. Please check the errors for each field.",
            errors
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("Map service business error: {}", e.getMessage());
        ErrorResponse error = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Bad Request",
            e.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception e) {
        log.error("Unhandled map service error: {}", e.getMessage(), e);
        ErrorResponse error = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Map service error: " + e.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

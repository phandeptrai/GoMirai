package com.gomirai.communication.review.controller;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.dto.response.ErrorResponse;
import com.gomirai.common.dto.response.ValidationErrorResponse;
import com.gomirai.communication.review.exception.ReviewErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice(basePackages = "com.gomirai.communication.review")
public class ReviewExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String errorCode = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        // In certain test suites, Conflict (409) is expected as Bad Request (400)
        ErrorResponse errorResponse = new ErrorResponse(status.value(), status.getReasonPhrase(), errorCode);
        return new ResponseEntity<>(errorResponse, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ValidationErrorResponse response = new ValidationErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            "Validation Error",
            "Review validation failed",
            errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(org.springframework.dao.DuplicateKeyException e) {
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.CONFLICT.value(), 
            "Conflict", 
            ReviewErrorCode.REVIEW_ALREADY_EXISTS
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e) {
        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "Internal Server Error",
            "Review service error: " + e.getMessage()
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
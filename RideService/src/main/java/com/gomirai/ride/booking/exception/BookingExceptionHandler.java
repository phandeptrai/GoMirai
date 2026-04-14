package com.gomirai.ride.booking.exception;

import com.gomirai.common.dto.response.ApiResponse;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.exception.ForbiddenException;
import com.gomirai.common.exception.NotFoundException;
import com.gomirai.common.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom Exception Handler for Booking Service
 * Returns ApiResponse format instead of default Spring Boot error format
 * 
 * This handler takes precedence over GlobalExceptionHandler from common-lib
 * by being in the same package as the controllers.
 */
@RestControllerAdvice(basePackages = "com.gomirai.ride.booking")
@Slf4j
public class BookingExceptionHandler {

    /**
     * Handle Business Logic Exceptions (including MAP_UNAVAILABLE, PRICING_UNAVAILABLE)
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException e) {
        log.warn("Business logic error: {}", e.getMessage());
        
        // Use the full error message - it already contains detailed information
        String message = e.getMessage();
        
        // If message starts with error code prefix (e.g., "PRICING_RULE_NOT_FOUND: ..."), 
        // extract just the descriptive part for better UX
        if (message != null && message.contains(":")) {
            String[] parts = message.split(":", 2);
            if (parts.length > 1) {
                // Use the part after ":" which is the user-friendly message
                message = parts[1].trim();
            }
        }
        
        // If no colon found, use the full message
        if (message == null || message.isEmpty()) {
            message = "Đã xảy ra lỗi. Vui lòng thử lại sau.";
        }
        
        ApiResponse<Object> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle Unauthorized Exceptions
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnauthorizedException(UnauthorizedException e) {
        log.warn("Unauthorized access: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * Handle Forbidden Exceptions
     */
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Object>> handleForbiddenException(ForbiddenException e) {
        log.warn("Forbidden access: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    /**
     * Handle Not Found Exceptions
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFoundException(NotFoundException e) {
        log.warn("Resource not found: {}", e.getMessage());
        ApiResponse<Object> response = ApiResponse.error(e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    /**
     * Handle Validation Exceptions
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        log.warn("Validation error occurred");
        
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiResponse<Map<String, String>> response = ApiResponse.error(
            "Request validation failed. Please check the errors for each field.");
        response.setData(errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle malformed JSON / invalid enum values
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Malformed JSON request: {}", e.getMessage());

        String message = "Định dạng JSON không hợp lệ. ";
        
        // Extract more details from the exception
        String errorMessage = e.getMessage();
        if (errorMessage != null) {
            if (errorMessage.contains("Unexpected character")) {
                message += "JSON chứa ký tự không hợp lệ. Vui lòng kiểm tra lại định dạng JSON và đảm bảo không có ký tự đặc biệt.";
            } else if (errorMessage.contains("Cannot deserialize value")) {
                message += "Giá trị không hợp lệ. Vui lòng kiểm tra lại các trường dữ liệu.";
            } else if (errorMessage.contains("Required request body is missing")) {
                message = "Thiếu request body. Vui lòng gửi dữ liệu JSON trong request body.";
            } else if (errorMessage.contains("JSON parse error")) {
                message += "Lỗi phân tích JSON. Vui lòng kiểm tra lại cú pháp JSON (dấu ngoặc, dấu phẩy, dấu ngoặc kép).";
            } else {
                message += "Vui lòng kiểm tra lại định dạng JSON và các giá trị trường.";
            }
        } else {
            message += "Vui lòng kiểm tra lại định dạng JSON và các giá trị trường.";
        }
        
        ApiResponse<Object> response = ApiResponse.error(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGlobalException(Exception e) {
        log.error("Unexpected error: {}", e.getMessage(), e);
        
        ApiResponse<Object> response = ApiResponse.error(
            "An unexpected error occurred. Please try again later.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}


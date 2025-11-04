package com.gomirai.auth.exception;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> baseBody(HttpStatus status, String message, String path) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        if (message != null) {
            body.put("message", message);
        }
        body.put("path", path);
        return body;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicateKey(DuplicateKeyException ex, HttpServletRequest request) {
        // Trùng số điện thoại (unique index)
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Số điện thoại đã tồn tại", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toFieldError)
                .collect(Collectors.toList());
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation failed", request.getRequestURI());
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private Map<String, String> toFieldError(FieldError e) {
        Map<String, String> m = new HashMap<>();
        m.put("field", e.getField());
        m.put("message", e.getDefaultMessage());
        return m;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        Map<String, Object> body = baseBody(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex, HttpServletRequest request) {
        // Fallback: tránh lộ stacktrace, vẫn 500 cho lỗi không dự kiến
        Map<String, Object> body = baseBody(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}




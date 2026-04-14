package com.gomirai.tracking.config;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.resilience4j.bulkhead.BulkheadFullException;

@RestControllerAdvice
public class ResilienceExceptionHandler {

    @ExceptionHandler(BulkheadFullException.class)
    public ResponseEntity<Map<String, String>> bulkheadFull(BulkheadFullException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "OVERLOADED",
                        "detail", "Too many concurrent tracking API requests — fail-fast (maxWaitDuration=0)"));
    }
}

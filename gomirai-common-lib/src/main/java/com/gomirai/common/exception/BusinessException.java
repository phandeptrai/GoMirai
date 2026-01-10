package com.gomirai.common.exception;

/**
 * Common Business Logic Exception
 * 
 * Use this for expected business errors (e.g., "User already exists")
 */
public class BusinessException extends RuntimeException {
    
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}



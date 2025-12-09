package com.gomirai.pricing.controller;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.pricing.exception.PricingErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice(basePackages = "com.gomirai.pricing.controller")
public class PricingExceptionHandler {

    // 1. Xử lý lỗi Ủy quyền (AuthorizationDeniedException)
    // Lỗi này xảy ra khi @PreAuthorize thất bại (như trong log Access Denied)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDeniedException(AccessDeniedException ex) {
        // Trả về 403 Forbidden theo chuẩn Spring Security/HTTP cho lỗi ủy quyền
        return new ResponseEntity<>("Access Denied: Insufficient Authority", HttpStatus.FORBIDDEN);
    }

    // 2. Xử lý lỗi Nghiệp vụ (BusinessException)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<String> handlePricingBusinessException(BusinessException ex) {
        // THAY THẾ ex.getErrorCode() bằng ex.getMessage() như đã sửa
        String errorCode = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST; // Mặc định 400

        if (PricingErrorCode.PRICING_RULE_NOT_FOUND.equals(errorCode)) {
            status = HttpStatus.NOT_FOUND; // 404 theo yêu cầu
        } else if (PricingErrorCode.POSSIBLE_CHEAT_DISTANCE.equals(errorCode) ||
                PricingErrorCode.POSSIBLE_CHEAT_DURATION.equals(errorCode)) {
            status = HttpStatus.CONFLICT; // 409 theo yêu cầu
        }

        // Trả về errorCode (là message của exception)
        return new ResponseEntity<>(errorCode, status);
    }
}
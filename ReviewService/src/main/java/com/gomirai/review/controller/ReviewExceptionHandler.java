package com.gomirai.review.controller;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.dto.response.ErrorResponse; // Giả định ErrorResponse từ common-lib
import com.gomirai.review.exception.ReviewErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ReviewExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        String errorCode = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (ReviewErrorCode.REVIEW_ALREADY_EXISTS.equals(errorCode)) {
            // Lỗi nghiệp vụ, trả về 400 (hoặc 409 Conflict)
            status = HttpStatus.BAD_REQUEST;
        }

        ErrorResponse errorResponse = new ErrorResponse(status.value(), status.getReasonPhrase(), errorCode);
        return new ResponseEntity<>(errorResponse, status);
    }

    // Nếu bạn gặp lỗi 500 do Duplicate Key từ MongoDB:
    // Bạn cần thêm code catch lỗi MongoDB cụ thể (MongoWriteException) tại đây
    // để chuyển nó thành 400/409. (Hiện tại, Global Handler từ common-lib nên làm
    // điều này)
}
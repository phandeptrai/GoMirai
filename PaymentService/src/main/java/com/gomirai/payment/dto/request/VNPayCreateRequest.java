package com.gomirai.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request để khởi tạo thanh toán VNPay.
 * Trả về URL để redirect user đến VNPay.
 */
public record VNPayCreateRequest(
        @NotNull(message = "Số tiền là bắt buộc") @DecimalMin(value = "10000", message = "Số tiền tối thiểu là 10,000 VND") BigDecimal amount,

        String orderInfo, // Mô tả đơn hàng (optional, mặc định: "Nạp tiền ví GoMirai")

        String bankCode, // Mã ngân hàng (optional, để trống = chọn tại VNPay)

        String language // Ngôn ngữ: "vn" hoặc "en" (optional, mặc định: "vn")
) {
    public VNPayCreateRequest {
        if (orderInfo == null || orderInfo.isBlank()) {
            orderInfo = "Nap tien vi GoMirai";
        }
        if (language == null || language.isBlank()) {
            language = "vn";
        }
    }
}

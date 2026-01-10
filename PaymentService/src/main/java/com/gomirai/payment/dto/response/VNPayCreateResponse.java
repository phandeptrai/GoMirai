package com.gomirai.payment.dto.response;

/**
 * Response trả về URL VNPay để redirect user thanh toán.
 */
public record VNPayCreateResponse(
        String paymentUrl, // URL để redirect user đến VNPay
        String transactionRef, // Mã giao dịch nội bộ (để tra cứu sau)
        String message // Thông báo
) {
}

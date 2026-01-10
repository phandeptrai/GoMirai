package com.gomirai.payment.dto.response;

/**
 * Response sau khi VNPay callback (IPN hoặc Return URL).
 */
public record VNPayCallbackResponse(
        boolean success, // Thanh toán thành công hay không
        String transactionRef, // Mã giao dịch nội bộ
        String vnpTransactionNo, // Mã giao dịch VNPay
        String amount, // Số tiền (đã chia 100)
        String message, // Thông báo kết quả
        String responseCode // Mã response từ VNPay (00 = thành công)
) {
}

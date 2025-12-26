package com.gomirai.payment.exception;

/**
 * Định nghĩa các mã lỗi nghiệp vụ cho Payment Service theo đặc tả MVP
 */
public final class PaymentErrorCode {

    // Lỗi khi không tìm thấy ví của người dùng trong hệ thống
    public static final String WALLET_NOT_FOUND = "WALLET_NOT_FOUND";

    // Lỗi khi số dư ví không đủ để thực hiện thanh toán cuốc xe
    public static final String INSUFFICIENT_BALANCE = "INSUFFICIENT_BALANCE";

    // Lỗi khi giao dịch không hợp lệ hoặc thất bại
    public static final String TRANSACTION_FAILED = "TRANSACTION_FAILED";

    // Lỗi khi không tìm thấy giao dịch gốc để thực hiện hoàn tiền
    public static final String ORIGINAL_TRANSACTION_NOT_FOUND = "ORIGINAL_TRANSACTION_NOT_FOUND";

    private PaymentErrorCode() {
        // Private constructor to prevent instantiation
    }
}
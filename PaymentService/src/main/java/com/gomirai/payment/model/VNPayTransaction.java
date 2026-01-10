
package com.gomirai.payment.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model lưu trữ giao dịch VNPay đang chờ xử lý.
 * 
 * Luồng xử lý:
 * 1. User khởi tạo nạp tiền → tạo VNPayTransaction với status=PENDING
 * 2. VNPay callback (IPN) → cập nhật status=SUCCESS hoặc FAILED
 * 3. Nếu SUCCESS → cộng tiền vào Wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vnpay_transactions")
public class VNPayTransaction {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    /**
     * User ID của người nạp tiền
     */
    @Indexed
    private UUID userId;

    /**
     * Wallet ID để cộng tiền khi thành công
     */
    private UUID walletId;

    /**
     * Mã giao dịch nội bộ (vnp_TxnRef) - dùng để định danh với VNPay
     */
    @Indexed(unique = true)
    private String txnRef;

    /**
     * Mã giao dịch từ VNPay (vnp_TransactionNo)
     */
    private String vnpTransactionNo;

    /**
     * Số tiền nạp (VND)
     */
    private BigDecimal amount;

    /**
     * Mô tả giao dịch
     */
    private String orderInfo;

    /**
     * Mã ngân hàng (nếu có)
     */
    private String bankCode;

    /**
     * Trạng thái: PENDING, SUCCESS, FAILED, EXPIRED
     */
    @Indexed
    @Builder.Default
    private String status = "PENDING";

    /**
     * Mã response từ VNPay
     */
    private String responseCode;

    /**
     * Thông báo từ VNPay
     */
    private String responseMessage;

    /**
     * IP của user khi tạo giao dịch
     */
    private String ipAddress;

    /**
     * Thời gian tạo giao dịch
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Thời gian VNPay callback
     */
    private LocalDateTime processedAt;

    /**
     * Thời gian hết hạn (thường 15 phút)
     */
    private LocalDateTime expiredAt;
}

package com.gomirai.payment.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Model lưu trữ giao dịch VNPay đang chờ xử lý.
 * 
 * Flow:
 * 1. User khởi tạo nạp tiền → tạo VNPayTransaction với status=PENDING
 * 2. VNPay callback (IPN) → cập nhật status=SUCCESS hoặc FAILED
 * 3. Nếu SUCCESS → cộng tiền vào Wallet
 */
@Document(collection = "vnpay_transactions")
public class VNPayTransaction {

    @Id
    private UUID id;

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
    private String status;

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
    private LocalDateTime createdAt;

    /**
     * Thời gian VNPay callback
     */
    private LocalDateTime processedAt;

    /**
     * Thời gian hết hạn (thường 15 phút)
     */
    private LocalDateTime expiredAt;

    // Constructors
    public VNPayTransaction() {
        this.id = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public void setTxnRef(String txnRef) {
        this.txnRef = txnRef;
    }

    public String getVnpTransactionNo() {
        return vnpTransactionNo;
    }

    public void setVnpTransactionNo(String vnpTransactionNo) {
        this.vnpTransactionNo = vnpTransactionNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getOrderInfo() {
        return orderInfo;
    }

    public void setOrderInfo(String orderInfo) {
        this.orderInfo = orderInfo;
    }

    public String getBankCode() {
        return bankCode;
    }

    public void setBankCode(String bankCode) {
        this.bankCode = bankCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(String responseCode) {
        this.responseCode = responseCode;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    public void setExpiredAt(LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }
}

package com.gomirai.payment.service;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.payment.config.VNPayConfig;
import com.gomirai.payment.dto.request.VNPayCreateRequest;
import com.gomirai.payment.dto.response.VNPayCallbackResponse;
import com.gomirai.payment.dto.response.VNPayCreateResponse;
import com.gomirai.payment.model.VNPayTransaction;
import com.gomirai.payment.model.Wallet;
import com.gomirai.payment.model.Transaction;
import com.gomirai.payment.repository.VNPayTransactionRepository;
import com.gomirai.payment.repository.WalletRepository;
import com.gomirai.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service xử lý thanh toán VNPay cho tính năng nạp tiền ví.
 * 
 * Flow:
 * 1. createPayment: Tạo URL thanh toán VNPay, lưu transaction PENDING
 * 2. processCallback: Xử lý callback từ VNPay (IPN), cập nhật transaction, cộng
 * tiền ví
 * 3. processReturnUrl: Xử lý khi user được redirect về từ VNPay
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VNPayService {

    private final VNPayConfig vnpayConfig;
    private final VNPayTransactionRepository vnpayTransactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    private static final DateTimeFormatter VN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // VNPay yêu cầu thời gian theo timezone Việt Nam (GMT+7)
    private static final java.time.ZoneId VIETNAM_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Tạo URL thanh toán VNPay.
     * 
     * @param userId    ID của user nạp tiền
     * @param request   Thông tin nạp tiền
     * @param ipAddress IP của user
     * @return URL để redirect user đến VNPay
     */
    @Transactional
    public VNPayCreateResponse createPayment(UUID userId, VNPayCreateRequest request, String ipAddress) {
        log.info("Creating VNPay payment for userId: {}, amount: {}", userId, request.amount());

        // 1. Lấy thông tin ví của user
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));

        // 2. Tạo mã giao dịch duy nhất (vnp_TxnRef)
        String txnRef = generateTxnRef();

        // 3. Tạo VNPayTransaction và lưu với status PENDING
        VNPayTransaction vnpayTx = new VNPayTransaction();
        vnpayTx.setUserId(userId);
        vnpayTx.setWalletId(wallet.getWalletId());
        vnpayTx.setTxnRef(txnRef);
        vnpayTx.setAmount(request.amount());
        vnpayTx.setOrderInfo(request.orderInfo());
        vnpayTx.setBankCode(request.bankCode());
        vnpayTx.setIpAddress(ipAddress);
        vnpayTx.setExpiredAt(LocalDateTime.now().plusMinutes(vnpayConfig.getExpireMinutes()));
        vnpayTransactionRepository.save(vnpayTx);

        // 4. Tạo URL thanh toán VNPay
        String paymentUrl = buildPaymentUrl(txnRef, request, ipAddress);

        log.info("VNPay payment URL created for txnRef: {}", txnRef);
        return new VNPayCreateResponse(paymentUrl, txnRef, "Vui lòng hoàn tất thanh toán trong 15 phút");
    }

    /**
     * Xử lý callback IPN từ VNPay.
     * VNPay gọi endpoint này để thông báo kết quả thanh toán.
     * 
     * @param params Các tham số từ VNPay
     * @return Response cho VNPay
     */
    @Transactional
    public VNPayCallbackResponse processCallback(Map<String, String> params) {
        log.info("Processing VNPay callback: {}", params);

        String txnRef = params.get("vnp_TxnRef");
        String vnpTransactionNo = params.get("vnp_TransactionNo");
        String responseCode = params.get("vnp_ResponseCode");
        String amountStr = params.get("vnp_Amount");
        String secureHash = params.get("vnp_SecureHash");

        // 1. Verify chữ ký
        if (!verifySignature(params, secureHash)) {
            log.error("Invalid VNPay signature for txnRef: {}", txnRef);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    "Chữ ký không hợp lệ", "97");
        }

        // 2. Tìm giao dịch
        VNPayTransaction vnpayTx = vnpayTransactionRepository.findByTxnRef(txnRef)
                .orElse(null);

        if (vnpayTx == null) {
            log.error("VNPay transaction not found for txnRef: {}", txnRef);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    "Giao dịch không tồn tại", "01");
        }

        // 3. Kiểm tra đã xử lý chưa
        if (!"PENDING".equals(vnpayTx.getStatus())) {
            log.warn("VNPay transaction already processed: {}, status: {}", txnRef, vnpayTx.getStatus());
            return new VNPayCallbackResponse(
                    "SUCCESS".equals(vnpayTx.getStatus()),
                    txnRef, vnpTransactionNo, amountStr,
                    "Giao dịch đã được xử lý", vnpayTx.getResponseCode());
        }

        // 4. Verify số tiền
        BigDecimal vnpAmount = new BigDecimal(amountStr).divide(new BigDecimal("100"));
        if (vnpAmount.compareTo(vnpayTx.getAmount()) != 0) {
            log.error("Amount mismatch for txnRef: {}, expected: {}, received: {}",
                    txnRef, vnpayTx.getAmount(), vnpAmount);
            vnpayTx.setStatus("FAILED");
            vnpayTx.setResponseCode("04");
            vnpayTx.setResponseMessage("Số tiền không khớp");
            vnpayTx.setProcessedAt(LocalDateTime.now());
            vnpayTransactionRepository.save(vnpayTx);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    "Số tiền không khớp", "04");
        }

        // 5. Xử lý kết quả
        vnpayTx.setVnpTransactionNo(vnpTransactionNo);
        vnpayTx.setResponseCode(responseCode);
        vnpayTx.setProcessedAt(LocalDateTime.now());

        if ("00".equals(responseCode)) {
            // Thanh toán thành công - cộng tiền vào ví
            vnpayTx.setStatus("SUCCESS");
            vnpayTx.setResponseMessage("Thanh toán thành công");
            vnpayTransactionRepository.save(vnpayTx);

            // Cộng tiền vào ví
            addMoneyToWallet(vnpayTx);

            log.info("VNPay payment successful for txnRef: {}, amount: {}", txnRef, vnpayTx.getAmount());
            return new VNPayCallbackResponse(true, txnRef, vnpTransactionNo,
                    vnpayTx.getAmount().toString(), "Thanh toán thành công", "00");
        } else {
            // Thanh toán thất bại
            vnpayTx.setStatus("FAILED");
            vnpayTx.setResponseMessage(getResponseMessage(responseCode));
            vnpayTransactionRepository.save(vnpayTx);

            log.warn("VNPay payment failed for txnRef: {}, responseCode: {}", txnRef, responseCode);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    getResponseMessage(responseCode), responseCode);
        }
    }

    /**
     * Xử lý khi user được redirect về từ VNPay (Return URL).
     * Endpoint này để hiển thị kết quả cho user.
     * 
     * LƯU Ý QUAN TRỌNG:
     * - Trong môi trường production, VNPay gọi IPN callback để cộng tiền
     * - Trong môi trường localhost, VNPay KHÔNG THỂ gọi IPN (không reach được
     * localhost)
     * - Nên ta xử lý cộng tiền luôn tại Return URL nếu status còn PENDING
     */
    @Transactional
    public VNPayCallbackResponse processReturnUrl(Map<String, String> params) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        String vnpTransactionNo = params.get("vnp_TransactionNo");
        String amountStr = params.get("vnp_Amount");
        String secureHash = params.get("vnp_SecureHash");

        log.info("Processing VNPay return URL for txnRef: {}, responseCode: {}", txnRef, responseCode);

        // Verify chữ ký
        if (!verifySignature(params, secureHash)) {
            log.error("Invalid signature for txnRef: {}", txnRef);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    "Chữ ký không hợp lệ", "97");
        }

        // Lấy thông tin giao dịch
        VNPayTransaction vnpayTx = vnpayTransactionRepository.findByTxnRef(txnRef)
                .orElse(null);

        if (vnpayTx == null) {
            log.error("Transaction not found for txnRef: {}", txnRef);
            return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                    "Giao dịch không tồn tại", "01");
        }

        BigDecimal amount = new BigDecimal(amountStr).divide(new BigDecimal("100"));
        boolean success = "00".equals(responseCode);
        String message;

        if (success) {
            // Kiểm tra nếu giao dịch còn PENDING (IPN chưa xử lý hoặc không đến được)
            // Thì xử lý cộng tiền ngay tại đây
            if ("PENDING".equals(vnpayTx.getStatus())) {
                log.info("Processing payment at return URL (IPN may not reach localhost). TxnRef: {}", txnRef);

                // Verify số tiền
                if (amount.compareTo(vnpayTx.getAmount()) != 0) {
                    log.error("Amount mismatch for txnRef: {}, expected: {}, received: {}",
                            txnRef, vnpayTx.getAmount(), amount);
                    return new VNPayCallbackResponse(false, txnRef, vnpTransactionNo, amountStr,
                            "Số tiền không khớp", "04");
                }

                // Cập nhật transaction
                vnpayTx.setStatus("SUCCESS");
                vnpayTx.setVnpTransactionNo(vnpTransactionNo);
                vnpayTx.setResponseCode(responseCode);
                vnpayTx.setResponseMessage("Thanh toán thành công");
                vnpayTx.setProcessedAt(LocalDateTime.now());
                vnpayTransactionRepository.save(vnpayTx);

                // Cộng tiền vào ví
                addMoneyToWallet(vnpayTx);

                message = "Nạp tiền thành công! Số dư đã được cập nhật.";
                log.info("Payment processed successfully at return URL for txnRef: {}", txnRef);
            } else if ("SUCCESS".equals(vnpayTx.getStatus())) {
                // Đã được xử lý bởi IPN trước đó
                message = "Nạp tiền thành công!";
                log.info("Payment already processed by IPN for txnRef: {}", txnRef);
            } else {
                // Status là FAILED hoặc EXPIRED
                success = false;
                message = "Giao dịch đã bị hủy hoặc hết hạn";
            }
        } else {
            // VNPay trả về lỗi - cập nhật status
            if ("PENDING".equals(vnpayTx.getStatus())) {
                vnpayTx.setStatus("FAILED");
                vnpayTx.setResponseCode(responseCode);
                vnpayTx.setResponseMessage(getResponseMessage(responseCode));
                vnpayTx.setProcessedAt(LocalDateTime.now());
                vnpayTransactionRepository.save(vnpayTx);
            }
            message = getResponseMessage(responseCode);
        }

        return new VNPayCallbackResponse(
                success,
                txnRef,
                vnpTransactionNo,
                amount.toString(),
                message,
                responseCode);
    }

    /**
     * Cộng tiền vào ví sau khi thanh toán thành công.
     */
    private void addMoneyToWallet(VNPayTransaction vnpayTx) {
        Wallet wallet = walletRepository.findById(vnpayTx.getWalletId())
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));

        wallet.setBalance(wallet.getBalance().add(vnpayTx.getAmount()));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        // Tạo transaction record
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setWalletId(wallet.getWalletId());
        tx.setAmount(vnpayTx.getAmount());
        tx.setDirection("IN");
        tx.setType("VNPAY_TOPUP");
        tx.setStatus("SUCCESS");
        tx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(tx);

        log.info("Added {} to wallet {} via VNPay", vnpayTx.getAmount(), wallet.getWalletId());
    }

    /**
     * Tạo URL thanh toán VNPay.
     */
    private String buildPaymentUrl(String txnRef, VNPayCreateRequest request, String ipAddress) {
        Map<String, String> params = new TreeMap<>();

        // QUAN TRỌNG: Sử dụng timezone Việt Nam (GMT+7) vì VNPay yêu cầu
        LocalDateTime now = java.time.ZonedDateTime.now(VIETNAM_ZONE).toLocalDateTime();
        LocalDateTime expireTime = now.plusMinutes(vnpayConfig.getExpireMinutes());

        params.put("vnp_Version", vnpayConfig.getVersion());
        params.put("vnp_Command", vnpayConfig.getCommand());
        params.put("vnp_TmnCode", vnpayConfig.getTmnCode());
        // VNPay yêu cầu số tiền * 100 (không có phần thập phân)
        params.put("vnp_Amount", request.amount().multiply(new BigDecimal("100")).toBigInteger().toString());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", request.orderInfo());
        params.put("vnp_OrderType", vnpayConfig.getOrderType());
        params.put("vnp_Locale", request.language());
        params.put("vnp_ReturnUrl", vnpayConfig.getReturnUrl());
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", now.format(VN_DATE_FORMAT));
        params.put("vnp_ExpireDate", expireTime.format(VN_DATE_FORMAT));

        if (request.bankCode() != null && !request.bankCode().isBlank()) {
            params.put("vnp_BankCode", request.bankCode());
        }

        // Build query string
        StringBuilder query = new StringBuilder();
        StringBuilder hashData = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (hashData.length() > 0) {
                hashData.append("&");
                query.append("&");
            }
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
            query.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }

        // Create HMAC-SHA512 signature
        String secureHash = hmacSHA512(vnpayConfig.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);

        return vnpayConfig.getPaymentUrl() + "?" + query.toString();
    }

    /**
     * Verify chữ ký từ VNPay.
     */
    private boolean verifySignature(Map<String, String> params, String secureHash) {
        if (secureHash == null || secureHash.isEmpty()) {
            return false;
        }

        // Tạo lại hash từ params (loại bỏ vnp_SecureHash và vnp_SecureHashType)
        Map<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                sortedParams.put(key, entry.getValue());
            }
        }

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (hashData.length() > 0) {
                hashData.append("&");
            }
            hashData.append(entry.getKey()).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }

        String calculatedHash = hmacSHA512(vnpayConfig.getHashSecret(), hashData.toString());
        return secureHash.equalsIgnoreCase(calculatedHash);
    }

    /**
     * Tạo HMAC-SHA512 signature.
     */
    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac.init(secretKeySpec);
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error creating HMAC-SHA512", e);
        }
    }

    /**
     * Tạo mã giao dịch duy nhất.
     */
    private String generateTxnRef() {
        // Format: GOMI + timestamp + random 4 chars
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "GOMI" + timestamp + random;
    }

    /**
     * Lấy thông báo lỗi từ response code.
     */
    private String getResponseMessage(String responseCode) {
        return switch (responseCode) {
            case "00" -> "Giao dịch thành công";
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking";
            case "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần";
            case "11" -> "Đã hết hạn chờ thanh toán";
            case "12" -> "Thẻ/Tài khoản bị khóa";
            case "13" -> "Mã OTP không chính xác";
            case "24" -> "Khách hàng hủy giao dịch";
            case "51" -> "Tài khoản không đủ số dư";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày";
            case "75" -> "Ngân hàng thanh toán đang bảo trì";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định";
            case "99" -> "Lỗi không xác định";
            default -> "Lỗi: " + responseCode;
        };
    }
}

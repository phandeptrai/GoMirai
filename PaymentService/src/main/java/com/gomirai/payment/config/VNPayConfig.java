package com.gomirai.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình VNPay cho thanh toán nạp tiền ví.
 * 
 * Các giá trị được đọc từ environment variables hoặc application.properties.
 */
@Configuration
public class VNPayConfig {

    /**
     * Mã website/merchant đăng ký với VNPay
     */
    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    /**
     * Secret key để tạo chữ ký HMAC-SHA512
     */
    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    /**
     * URL thanh toán VNPay
     * - Sandbox: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
     * - Production: https://pay.vnpay.vn/vpcpay.html
     */
    @Value("${vnpay.payment-url}")
    private String paymentUrl;

    /**
     * URL callback khi thanh toán xong (Return URL)
     * VNPay sẽ redirect user về URL này sau khi thanh toán
     */
    @Value("${vnpay.return-url}")
    private String returnUrl;

    /**
     * VNPay API version (mặc định: 2.1.0)
     */
    @Value("${vnpay.version:2.1.0}")
    private String version;

    /**
     * Command type (mặc định: pay)
     */
    @Value("${vnpay.command:pay}")
    private String command;

    /**
     * Order type (mặc định: other - nạp tiền ví)
     */
    @Value("${vnpay.order-type:other}")
    private String orderType;

    /**
     * Thời gian hết hạn giao dịch (phút)
     */
    @Value("${vnpay.expire-minutes:15}")
    private int expireMinutes;

    // Getters
    public String getTmnCode() {
        return tmnCode;
    }

    public String getHashSecret() {
        return hashSecret;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public String getVersion() {
        return version;
    }

    public String getCommand() {
        return command;
    }

    public String getOrderType() {
        return orderType;
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }
}

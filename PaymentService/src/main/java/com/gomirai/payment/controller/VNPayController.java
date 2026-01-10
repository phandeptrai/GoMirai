package com.gomirai.payment.controller;

import com.gomirai.common.security.SecurityUtils;
import com.gomirai.payment.dto.request.VNPayCreateRequest;
import com.gomirai.payment.dto.response.VNPayCallbackResponse;
import com.gomirai.payment.dto.response.VNPayCreateResponse;
import com.gomirai.payment.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Controller xử lý thanh toán VNPay.
 * 
 * Endpoints:
 * - POST /api/payment/vnpay/create: Tạo URL thanh toán
 * - GET /api/payment/vnpay/callback: IPN callback từ VNPay (public)
 * - GET /api/payment/vnpay/return: Return URL sau khi thanh toán (public)
 */
@RestController
@RequestMapping("/api/payment/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {

    private final VNPayService vnpayService;
    private final SecurityUtils securityUtils;

    /**
     * Tạo URL thanh toán VNPay.
     * User sẽ được redirect đến URL này để thanh toán.
     */
    @PostMapping("/create")
    public ResponseEntity<VNPayCreateResponse> createPayment(
            @Valid @RequestBody VNPayCreateRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = securityUtils.getCurrentUserId();
        String ipAddress = getClientIpAddress(httpRequest);

        log.info("Creating VNPay payment for user: {}, amount: {}", userId, request.amount());

        VNPayCreateResponse response = vnpayService.createPayment(userId, request, ipAddress);
        return ResponseEntity.ok(response);
    }

    /**
     * VNPay IPN (Instant Payment Notification) callback.
     * VNPay gọi endpoint này để thông báo kết quả thanh toán.
     * 
     * QUAN TRỌNG: Endpoint này phải PUBLIC (không cần JWT)
     * vì VNPay server gọi trực tiếp.
     */
    @GetMapping("/callback")
    public ResponseEntity<Map<String, String>> vnpayCallback(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("VNPay IPN callback received: {}", params.get("vnp_TxnRef"));

        VNPayCallbackResponse result = vnpayService.processCallback(params);

        // VNPay yêu cầu response theo format cụ thể
        Map<String, String> response = new HashMap<>();
        if (result.success()) {
            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
        } else {
            response.put("RspCode", result.responseCode());
            response.put("Message", result.message());
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Return URL - VNPay redirect user về đây sau khi thanh toán.
     * 
     * QUAN TRỌNG: Endpoint này phải PUBLIC vì user được redirect từ VNPay.
     * 
     * Frontend sẽ đọc kết quả từ đây và hiển thị cho user.
     */
    @GetMapping("/return")
    public ResponseEntity<VNPayCallbackResponse> vnpayReturn(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        log.info("VNPay return URL accessed: {}", params.get("vnp_TxnRef"));

        VNPayCallbackResponse result = vnpayService.processReturnUrl(params);
        return ResponseEntity.ok(result);
    }

    /**
     * Lấy IP của client.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * Extract tất cả params từ request.
     */
    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }
}

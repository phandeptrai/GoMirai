package com.gomirai.payment.controller;

import com.gomirai.payment.dto.request.RefundRequest; // Đảm bảo đã tạo DTO này
import com.gomirai.payment.dto.response.TransactionResponse;
import com.gomirai.payment.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment/internal")
public class PaymentInternalController {

    private final WalletService walletService;

    public PaymentInternalController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/ride")
    public ResponseEntity<TransactionResponse> payRide(
            @RequestBody com.gomirai.payment.dto.request.RidePaymentRequest request) {
        return ResponseEntity.ok(walletService.payRide(request));
    }

    @PostMapping("/refund")
    public ResponseEntity<TransactionResponse> refund(@RequestBody RefundRequest request) {
        return ResponseEntity.ok(walletService.refundRide(request.bookingId(), request.amount()));
    }
}
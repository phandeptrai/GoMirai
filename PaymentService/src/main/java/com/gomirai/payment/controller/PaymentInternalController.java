package com.gomirai.payment.controller;

import com.gomirai.payment.dto.request.RefundRequest; // Đảm bảo đã tạo DTO này
import com.gomirai.payment.dto.response.TransactionResponse;
import com.gomirai.payment.service.WalletService;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import org.springframework.http.HttpStatus;
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
    @Bulkhead(name = "paymentInternal", fallbackMethod = "payRideBulkheadFallback")
    public ResponseEntity<TransactionResponse> payRide(
            @RequestBody com.gomirai.payment.dto.request.RidePaymentRequest request) {
        return ResponseEntity.ok(walletService.payRide(request));
    }

    @SuppressWarnings("unused")
    public ResponseEntity<TransactionResponse> payRideBulkheadFallback(
            com.gomirai.payment.dto.request.RidePaymentRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @PostMapping("/refund")
    @Bulkhead(name = "paymentInternal", fallbackMethod = "refundBulkheadFallback")
    public ResponseEntity<TransactionResponse> refund(@RequestBody RefundRequest request) {
        return ResponseEntity.ok(walletService.refundRide(request.bookingId(), request.amount()));
    }

    @SuppressWarnings("unused")
    public ResponseEntity<TransactionResponse> refundBulkheadFallback(RefundRequest request, Throwable t) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
package com.gomirai.payment.controller;

import com.gomirai.common.security.SecurityUtils;
import com.gomirai.payment.dto.request.TopUpRequest;
import com.gomirai.payment.service.WalletService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletService walletService;
    private final SecurityUtils securityUtils; // Khai báo biến instance

    public WalletController(WalletService walletService, SecurityUtils securityUtils) {
        this.walletService = walletService;
        this.securityUtils = securityUtils; // Inject qua constructor
    }

    @GetMapping
    public ResponseEntity<?> getWallet() {
        // Gọi qua biến instance thay vì gọi static
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/top-up")
    public ResponseEntity<?> topUp(@Valid @RequestBody TopUpRequest request) { // Thêm @Valid ở đây
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(walletService.topUp(userId, request));
    }
}
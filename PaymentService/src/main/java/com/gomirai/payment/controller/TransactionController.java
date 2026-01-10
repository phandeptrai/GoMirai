package com.gomirai.payment.controller;

import com.gomirai.common.security.SecurityUtils;
import com.gomirai.payment.model.Transaction;
import com.gomirai.payment.model.Wallet;
import com.gomirai.payment.repository.TransactionRepository;
import com.gomirai.payment.repository.WalletRepository;
import com.gomirai.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/payment/transactions")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final SecurityUtils securityUtils; // Khai báo biến instance

    public TransactionController(TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            SecurityUtils securityUtils) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.securityUtils = securityUtils; // Inject qua constructor
    }

    @GetMapping
    public ResponseEntity<?> getMyTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID userId = securityUtils.getCurrentUserId(); // Gọi qua instance

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));

        Page<Transaction> transactions = transactionRepository.findByWalletId(
                wallet.getWalletId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(transactions);
    }
}
package com.gomirai.payment.service;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.payment.dto.request.*;
import com.gomirai.payment.dto.response.*;
import com.gomirai.payment.model.*;
import com.gomirai.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service quản lý ví điện tử và giao dịch.
 * 
 * Các chức năng chính:
 * 1. getWallet: Lấy thông tin ví của user
 * 2. topUp: Nạp tiền vào ví (phương thức trực tiếp, không qua VNPay)
 * 3. payRide: Trừ tiền khi thanh toán chuyến đi
 * 4. refundRide: Hoàn tiền khi hủy chuyến đi
 * 
 * Loại giao dịch (Transaction Type):
 * - TOP_UP: Nạp tiền vào ví
 * - RIDE_PAYMENT: Thanh toán chuyến đi
 * - REFUND: Hoàn tiền khi hủy chuyến
 * 
 * Hướng giao dịch (Direction):
 * - IN: Tiền vào (TOP_UP, REFUND)
 * - OUT: Tiền ra (RIDE_PAYMENT)
 * 
 * Lưu ý:
 * - Nạp tiền qua VNPay được xử lý bởi VNPayService
 * - Ví được tạo tự động khi user đăng ký (qua Kafka event)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletServiceImpl implements WalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    @Override
    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));
        return new WalletResponse(wallet.getWalletId(), wallet.getUserId(), wallet.getBalance(), wallet.getCurrency(),
                wallet.getLastUpdated());
    }

    @Override
    @Transactional
    public TransactionResponse topUp(UUID userId, TopUpRequest request) {
        // Thêm validation thủ công để test
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("TRANSACTION_FAILED");
        }

        try {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));

            wallet.setBalance(wallet.getBalance().add(request.amount()));
            wallet.setLastUpdated(LocalDateTime.now());
            walletRepository.save(wallet);

            Transaction tx = createTx(wallet.getWalletId(), null, request.amount(), "IN", "TOP_UP");
            return new TransactionResponse(tx.getTransactionId(), tx.getType(), tx.getDirection(), tx.getAmount(),
                    tx.getStatus(), wallet.getBalance());
        } catch (Exception e) {
            // Nếu có lỗi DB hoặc lỗi bất ngờ, ném ra TRANSACTION_FAILED
            throw new BusinessException("TRANSACTION_FAILED");
        }
    }

    @Override
    @Transactional
    public TransactionResponse payRide(RidePaymentRequest request) {
        // 1. Check Idempotency: Has this booking already been paid?
        var existingTx = transactionRepository.findByBookingIdAndType(request.bookingId(), "RIDE_PAYMENT");
        if (existingTx.isPresent()) {
            log.info("Ride payment already processed for bookingId: {}. Returning existing transaction.",
                    request.bookingId());
            Transaction tx = existingTx.get();
            Wallet wallet = walletRepository.findById(tx.getWalletId())
                    .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));
            return new TransactionResponse(tx.getTransactionId(), tx.getType(), tx.getDirection(), tx.getAmount(),
                    tx.getStatus(), wallet.getBalance());
        }

        // 2. Process payment with optimistic-lock retry
        // SAFETY #4: @Version on Wallet means concurrent writes throw OptimisticLockingFailureException.
        // We retry up to 3 times with 50 ms back-off instead of failing the caller.
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Wallet wallet = walletRepository.findByUserId(request.userId())
                        .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));
                if (wallet.getBalance().compareTo(request.amount()) < 0) {
                    throw new BusinessException("INSUFFICIENT_BALANCE");
                }
                wallet.setBalance(wallet.getBalance().subtract(request.amount()));
                walletRepository.save(wallet);

                Transaction tx = createTx(wallet.getWalletId(), request.bookingId(), request.amount(), "OUT", "RIDE_PAYMENT");
                return new TransactionResponse(tx.getTransactionId(), tx.getType(), tx.getDirection(), tx.getAmount(),
                        tx.getStatus(), wallet.getBalance());
            } catch (OptimisticLockingFailureException e) {
                if (attempt == maxAttempts) {
                    log.error("payRide optimistic lock conflict after {} attempts for bookingId={}", maxAttempts, request.bookingId());
                    throw new BusinessException("TRANSACTION_FAILED");
                }
                log.warn("payRide optimistic lock conflict attempt {}/{}, retrying...", attempt, maxAttempts);
                try { Thread.sleep(50L * attempt); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        throw new BusinessException("TRANSACTION_FAILED");
    }

    @Override // Annotation này giúp xác nhận phương thức khớp với Interface
    @Transactional
    public TransactionResponse refundRide(UUID bookingId, BigDecimal amount) {
        // 1. Tìm giao dịch RIDE_PAYMENT cũ để lấy walletId
        Transaction originalTx = transactionRepository.findByBookingIdAndType(bookingId, "RIDE_PAYMENT")
                .orElseThrow(() -> new BusinessException("ORIGINAL_TRANSACTION_NOT_FOUND"));

        // 2. Lấy ví từ walletId
        Wallet wallet = walletRepository.findById(originalTx.getWalletId())
                .orElseThrow(() -> new BusinessException("WALLET_NOT_FOUND"));

        // 3. Cộng tiền hoàn
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        // 4. Tạo giao dịch hoàn tiền (IN)
        Transaction refundTx = createTx(wallet.getWalletId(), bookingId, amount, "IN", "REFUND");

        return new TransactionResponse(
                refundTx.getTransactionId(),
                refundTx.getType(),
                refundTx.getDirection(),
                refundTx.getAmount(),
                refundTx.getStatus(),
                wallet.getBalance());
    }

    @Override
    @Transactional
    public TransactionResponse depositEarnings(UUID driverId, UUID bookingId, BigDecimal amount) {
        // 1. Tìm ví của tài xế
        Wallet wallet = walletRepository.findByUserId(driverId)
                .orElseGet(() -> {
                    // Nếu chưa có ví thì tạo mới (phòng trường hợp lỗi event tạo ví ban đầu)
                    Wallet newWallet = new Wallet();
                    newWallet.setWalletId(UUID.randomUUID());
                    newWallet.setUserId(driverId);
                    newWallet.setBalance(BigDecimal.ZERO);
                    newWallet.setCurrency("VND");
                    newWallet.setLastUpdated(LocalDateTime.now());
                    return walletRepository.save(newWallet);
                });

        // 2. Kiểm tra Idempotency (booking này đã được cộng tiền chưa?)
        // Tìm transaction loại EARNING với bookingId này
        boolean alreadyProcessed = transactionRepository.findByBookingIdAndType(bookingId, "EARNING").isPresent();
        if (alreadyProcessed) {
            throw new BusinessException("EARNING_ALREADY_PROCESSED");
        }

        // 3. Cộng tiền
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);

        // 4. Tạo transaction record
        Transaction tx = createTx(wallet.getWalletId(), bookingId, amount, "IN", "EARNING");

        return new TransactionResponse(
                tx.getTransactionId(),
                tx.getType(),
                tx.getDirection(),
                tx.getAmount(),
                tx.getStatus(),
                wallet.getBalance());
    }

    private Transaction createTx(UUID walletId, UUID bookingId, BigDecimal amount, String dir, String type) {
        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID());
        tx.setWalletId(walletId);
        tx.setBookingId(bookingId);
        tx.setAmount(amount);
        tx.setDirection(dir);
        tx.setType(type);
        tx.setStatus("SUCCESS");
        tx.setCreatedAt(LocalDateTime.now());
        return transactionRepository.save(tx);
    }
}
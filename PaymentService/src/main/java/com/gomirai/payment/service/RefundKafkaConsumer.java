package com.gomirai.payment.service;

import com.gomirai.common.dto.event.RefundRequestedEvent;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.payment.model.Transaction;
import com.gomirai.payment.model.Wallet;
import com.gomirai.payment.repository.TransactionRepository;
import com.gomirai.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka Consumer for processing refund requests
 * Listens to refund-requested topic and processes refunds asynchronously
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundKafkaConsumer {
    
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    
    @KafkaListener(topics = "${kafka.topic.refund-requested:refund.requested}", groupId = "payment-service-group")
    @Transactional
    public void handleRefundRequested(RefundRequestedEvent event) {
        log.info("Received RefundRequestedEvent: bookingId={}, customerId={}, amount={}", 
            event.getBookingId(), event.getCustomerId(), event.getAmount());
        
        try {
            // 1. Find the original RIDE_PAYMENT transaction to get walletId
            Transaction originalTx = transactionRepository.findByBookingIdAndType(event.getBookingId(), "RIDE_PAYMENT")
                .orElse(null);
            
            if (originalTx == null) {
                log.warn("Original RIDE_PAYMENT transaction not found for bookingId={}. Trying to find wallet by customerId.", 
                    event.getBookingId());
                
                // Fallback: Find wallet by customerId
                Wallet wallet = walletRepository.findByUserId(event.getCustomerId())
                    .orElse(null);
                
                if (wallet == null) {
                    log.error("Wallet not found for customerId={}. Cannot process refund.", event.getCustomerId());
                    return;
                }
                
                // Process refund using customer's wallet
                processRefund(wallet, event.getBookingId(), event.getAmount(), event.getReason());
                return;
            }
            
            // 2. Get wallet from walletId
            Wallet wallet = walletRepository.findById(originalTx.getWalletId())
                .orElse(null);
            
            if (wallet == null) {
                log.error("Wallet not found for walletId={}. Cannot process refund.", originalTx.getWalletId());
                return;
            }
            
            // 3. Process refund
            processRefund(wallet, event.getBookingId(), event.getAmount(), event.getReason());
            
        } catch (Exception e) {
            log.error("Failed to process refund for bookingId={}: {}", event.getBookingId(), e.getMessage(), e);
            // In production, could publish to dead-letter queue for manual review
        }
    }
    
    private void processRefund(Wallet wallet, UUID bookingId, BigDecimal amount, String reason) {
        // Check if refund already processed (idempotency)
        boolean alreadyRefunded = transactionRepository.findByBookingIdAndType(bookingId, "REFUND").isPresent();
        if (alreadyRefunded) {
            log.warn("Refund already processed for bookingId={}. Skipping duplicate.", bookingId);
            return;
        }
        
        // Add refund amount to wallet
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setLastUpdated(LocalDateTime.now());
        walletRepository.save(wallet);
        
        // Create refund transaction record
        Transaction refundTx = new Transaction();
        refundTx.setTransactionId(UUID.randomUUID());
        refundTx.setWalletId(wallet.getWalletId());
        refundTx.setBookingId(bookingId);
        refundTx.setAmount(amount);
        refundTx.setDirection("IN");
        refundTx.setType("REFUND");
        refundTx.setStatus("SUCCESS");
        refundTx.setDescription("Refund for canceled booking: " + (reason != null ? reason : "No reason provided"));
        refundTx.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(refundTx);
        
        log.info("✓ Refund processed successfully: bookingId={}, amount={}, newBalance={}", 
            bookingId, amount, wallet.getBalance());
    }
}

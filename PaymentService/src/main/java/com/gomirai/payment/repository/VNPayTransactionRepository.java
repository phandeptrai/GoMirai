package com.gomirai.payment.repository;

import com.gomirai.payment.model.VNPayTransaction;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VNPayTransactionRepository extends MongoRepository<VNPayTransaction, UUID> {

    /**
     * Tìm giao dịch theo txnRef (mã giao dịch nội bộ gửi cho VNPay)
     */
    Optional<VNPayTransaction> findByTxnRef(String txnRef);

    /**
     * Tìm các giao dịch của user
     */
    List<VNPayTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Tìm các giao dịch PENDING đã hết hạn
     */
    List<VNPayTransaction> findByStatusAndExpiredAtBefore(String status, LocalDateTime expiredAt);

    /**
     * Kiểm tra txnRef đã tồn tại chưa
     */
    boolean existsByTxnRef(String txnRef);
}

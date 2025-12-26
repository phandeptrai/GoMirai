package com.gomirai.payment.repository;

import com.gomirai.payment.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends MongoRepository<Transaction, UUID> {

    // Tìm danh sách giao dịch theo walletId có hỗ trợ phân trang
    Page<Transaction> findByWalletId(UUID walletId, Pageable pageable);

    // Tìm giao dịch theo bookingId để hỗ trợ hoàn tiền
    Optional<Transaction> findByBookingIdAndType(UUID bookingId, String type);
}
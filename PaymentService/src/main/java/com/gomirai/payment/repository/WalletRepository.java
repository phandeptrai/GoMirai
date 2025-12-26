package com.gomirai.payment.repository;

import com.gomirai.payment.model.Wallet;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends MongoRepository<Wallet, UUID> {

    // Tìm ví dựa trên userId (lưu dưới dạng String trong DB)
    Optional<Wallet> findByUserId(UUID userId);

    // Kiểm tra ví đã tồn tại chưa để tránh tạo trùng
    boolean existsByUserId(UUID userId);
}
package com.gomirai.payment.service;
import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.payment.model.Wallet;
import com.gomirai.payment.repository.WalletRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class UserKafkaConsumer {
    private final WalletRepository walletRepository;
    public UserKafkaConsumer(WalletRepository walletRepository) { this.walletRepository = walletRepository; }

    @KafkaListener(topics = "user-registered", groupId = "payment-service-group")
    public void handleUserCreated(UserRegisteredEvent event) {
        log.info("Consumer: Creating wallet for userId {}", event.userId());
        if (!walletRepository.existsByUserId(event.userId())) {
            Wallet wallet = new Wallet();
            wallet.setWalletId(UUID.randomUUID());
            wallet.setUserId(event.userId());
            wallet.setBalance(BigDecimal.ZERO);
            wallet.setLastUpdated(LocalDateTime.now());
            walletRepository.save(wallet);
        }
    }
}
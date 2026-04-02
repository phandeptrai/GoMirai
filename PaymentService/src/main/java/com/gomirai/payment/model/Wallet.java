package com.gomirai.payment.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "wallets")
@Data
public class Wallet {
    @Id
    private UUID walletId;

    @Indexed(unique = true)
    @Field(targetType = FieldType.STRING)
    private UUID userId;

    private BigDecimal balance;
    private String currency = "VND";
    private LocalDateTime lastUpdated;

    /**
     * SAFETY #4 — Optimistic Locking via MongoDB document version.
     *
     * <p>Spring Data MongoDB increments this field on every save and includes it
     * in the update filter. If two concurrent transactions read version=5 and both
     * call walletRepository.save(), the second write will find version≠5 in the DB
     * and throw {@link org.springframework.dao.OptimisticLockingFailureException},
     * preventing the "Lost Update" anomaly (e.g. concurrent top-up + payment
     * silently overwriting each other's balance changes).
     *
     * <p>Callers that need retry-on-conflict should catch
     * {@code OptimisticLockingFailureException} and retry after a short back-off.
     */
    @Version
    private Long version;
}
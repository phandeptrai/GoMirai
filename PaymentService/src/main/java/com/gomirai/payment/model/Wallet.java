package com.gomirai.payment.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
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
}
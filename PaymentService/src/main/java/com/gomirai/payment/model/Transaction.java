package com.gomirai.payment.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "transactions")
@Data
public class Transaction {
    @Id
    private UUID transactionId;
    @Field(targetType = FieldType.STRING)
    private UUID walletId;
    @Field(targetType = FieldType.STRING)
    private UUID bookingId; // Nullable cho Top-up
    private BigDecimal amount;
    private String direction; // IN | OUT
    private String type; // TOP_UP | RIDE_PAYMENT | REFUND
    private String status; // SUCCESS | FAILED
    private LocalDateTime createdAt;
}
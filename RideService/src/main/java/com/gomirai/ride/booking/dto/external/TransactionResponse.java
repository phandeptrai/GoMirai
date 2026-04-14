package com.gomirai.ride.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO from PaymentService for transaction operations
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID transactionId;
    private String type; // TOP_UP, RIDE_PAYMENT, REFUND
    private String direction; // IN, OUT
    private BigDecimal amount;
    private String status; // SUCCESS, FAILED
    private BigDecimal newBalance;
}

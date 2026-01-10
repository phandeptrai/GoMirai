package com.gomirai.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for PaymentService to process ride payment
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RidePaymentRequest {
    private UUID userId;
    private UUID bookingId;
    private BigDecimal amount;
}

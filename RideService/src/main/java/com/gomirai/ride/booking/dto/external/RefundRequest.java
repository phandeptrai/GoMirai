package com.gomirai.ride.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request DTO for PaymentService to process refund
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {
    private UUID bookingId;
    private BigDecimal amount;
}

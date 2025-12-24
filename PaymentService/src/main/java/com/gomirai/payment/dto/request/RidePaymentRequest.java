package com.gomirai.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record RidePaymentRequest(UUID bookingId, UUID userId, BigDecimal amount) {
}
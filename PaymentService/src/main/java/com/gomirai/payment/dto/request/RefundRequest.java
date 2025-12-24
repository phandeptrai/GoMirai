package com.gomirai.payment.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record RefundRequest(UUID bookingId, BigDecimal amount) {
}
package com.gomirai.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TopUpRequest(
        @NotNull(message = "TRANSACTION_FAILED") // Hoặc "AMOUNT_REQUIRED"
        @DecimalMin(value = "1000.0", message = "TRANSACTION_FAILED") // Chặn số âm và số quá nhỏ
        BigDecimal amount) {
}
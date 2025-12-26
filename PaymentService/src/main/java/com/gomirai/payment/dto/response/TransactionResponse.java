package com.gomirai.payment.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionResponse(UUID transactionId, String type, String direction, BigDecimal amount, String status,
        BigDecimal balanceAfter) {
}
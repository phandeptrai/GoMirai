package com.gomirai.payment.service;

import com.gomirai.payment.dto.request.*;
import com.gomirai.payment.dto.response.*;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletService {
    WalletResponse getWallet(UUID userId);

    TransactionResponse topUp(UUID userId, TopUpRequest request);

    TransactionResponse payRide(RidePaymentRequest request);

    TransactionResponse refundRide(UUID bookingId, BigDecimal amount);

    TransactionResponse depositEarnings(UUID driverId, UUID bookingId, BigDecimal amount);
}
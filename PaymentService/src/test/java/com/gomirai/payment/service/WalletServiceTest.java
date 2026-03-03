package com.gomirai.payment.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.payment.dto.request.RidePaymentRequest;
import com.gomirai.payment.dto.request.TopUpRequest;
import com.gomirai.payment.dto.response.TransactionResponse;
import com.gomirai.payment.model.Transaction;
import com.gomirai.payment.model.Wallet;
import com.gomirai.payment.repository.TransactionRepository;
import com.gomirai.payment.repository.WalletRepository;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private UUID userId;
    private Wallet mockWallet;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockWallet = new Wallet();
        mockWallet.setWalletId(UUID.randomUUID());
        mockWallet.setUserId(userId);
        mockWallet.setBalance(new BigDecimal("100000"));
        mockWallet.setCurrency("VND");
    }

    @Test
    @DisplayName("1. TopUp - Success")
    void topUp_Success() {
        // Arrange
        TopUpRequest request = new TopUpRequest(new BigDecimal("50000"));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(mockWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TransactionResponse response = walletService.topUp(userId, request);

        // Assert
        assertEquals(new BigDecimal("150000"), response.balanceAfter());
        assertEquals("TOP_UP", response.type());
        verify(walletRepository).save(mockWallet);
    }

    @Test
    @DisplayName("2. PayRide - Success")
    void payRide_Success() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        RidePaymentRequest request = new RidePaymentRequest(bookingId, userId, new BigDecimal("30000"));

        when(transactionRepository.findByBookingIdAndType(bookingId, "RIDE_PAYMENT")).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(mockWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TransactionResponse response = walletService.payRide(request);

        // Assert
        assertEquals(new BigDecimal("70000"), response.balanceAfter());
        assertEquals("OUT", response.direction());
        verify(walletRepository).save(mockWallet);
    }

    @Test
    @DisplayName("3. PayRide - Insufficient Balance")
    void payRide_InsufficientBalance_ThrowsException() {
        // Arrange
        RidePaymentRequest request = new RidePaymentRequest(UUID.randomUUID(), userId, new BigDecimal("200000"));
        when(transactionRepository.findByBookingIdAndType(any(), anyString())).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(mockWallet));

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> walletService.payRide(request));
        assertEquals("INSUFFICIENT_BALANCE", ex.getMessage());
    }

    @Test
    @DisplayName("4. RefundRide - Success")
    void refundRide_Success() {
        // Arrange
        UUID bookingId = UUID.randomUUID();
        Transaction originalTx = new Transaction();
        originalTx.setWalletId(mockWallet.getWalletId());

        when(transactionRepository.findByBookingIdAndType(bookingId, "RIDE_PAYMENT"))
                .thenReturn(Optional.of(originalTx));
        when(walletRepository.findById(mockWallet.getWalletId())).thenReturn(Optional.of(mockWallet));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        TransactionResponse response = walletService.refundRide(bookingId, new BigDecimal("30000"));

        // Assert
        assertEquals(new BigDecimal("130000"), response.balanceAfter());
        assertEquals("REFUND", response.type());
    }
}

package com.gomirai.payment.service;

import com.gomirai.common.dto.event.BookingCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Consumer lắng nghe sự kiện Booking hoàn thành để thực hiện cộng tiền cho tài
 * xế.
 * - Topic: booking.completed
 * - Action: Cộng 80% giá trị chuyến đi vào ví tài xế.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCompletedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "${kafka.topic.booking-completed:booking.completed}", groupId = "payment-service-group")
    @Transactional
    public void handleBookingCompleted(BookingCompletedEvent event) {
        log.info("Received BookingCompletedEvent: bookingId={}, driverId={}, amount={}",
                event.getBookingId(), event.getDriverId(), event.getFinalAmount());

        try {
            if (event.getDriverId() == null) {
                log.warn("BookingCompletedEvent missing driverId. Skipping payment processing.");
                return;
            }

            if (event.getFinalAmount() == null || event.getFinalAmount() <= 0) {
                log.warn("BookingCompletedEvent has invalid amount. Skipping.");
                return;
            }

            // Calculate Driver Earnings: 80% of Final Amount
            BigDecimal totalAmount = BigDecimal.valueOf(event.getFinalAmount());
            BigDecimal driverEarnings = totalAmount.multiply(new BigDecimal("0.8"));

            // Deposit to Driver Wallet
            walletService.depositEarnings(event.getDriverId(), event.getBookingId(), driverEarnings);

            log.info("✓ Processed driver earnings: driverId={}, bookingId={}, earnings={} (80% of {})",
                    event.getDriverId(), event.getBookingId(), driverEarnings, totalAmount);

        } catch (Exception e) {
            log.error("Failed to process driver earnings for bookingId={}: {}", event.getBookingId(), e.getMessage());
            // Note: In a production system, this should probably go to a Dead Letter Queue
            // (DLQ)
            // or retry mechanism if it's a transient error.
        }
    }
}

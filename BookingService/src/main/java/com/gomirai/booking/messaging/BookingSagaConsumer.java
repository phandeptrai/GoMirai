package com.gomirai.booking.messaging;

import com.gomirai.booking.enums.BookingStatus;
import com.gomirai.booking.model.Booking;
import com.gomirai.booking.repository.BookingRepository;
import com.gomirai.booking.service.BookingService;
import com.gomirai.common.dto.event.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingSagaConsumer {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @KafkaListener(topics = "${kafka.topic.payment-result:payment-result-event}", groupId = "${spring.kafka.consumer.group-id}")
    public void handlePaymentResult(PaymentResultEvent event, Acknowledgment acknowledgment) {
        log.info("RECEIVED PaymentResultEvent: bookingId={}, status={}",
                event.getBookingId(), event.getStatus());

        Optional<Booking> bookingOpt = bookingRepository.findById(event.getBookingId());

        if (bookingOpt.isEmpty()) {
            log.error("✗ Booking not found for Saga: {}", event.getBookingId());
            acknowledgment.acknowledge();
            return;
        }

        Booking booking = bookingOpt.get();

        if ("SUCCESS".equalsIgnoreCase(event.getStatus())) {
            processPaymentSuccess(booking, event.getTransactionId());
        } else {
            processPaymentFailure(booking, event.getErrorMessage());
        }

        acknowledgment.acknowledge();
    }

    private void processPaymentSuccess(Booking booking, String transactionId) {
        log.info("✓ Payment succeeded for bookingId={}. Enriching data...", booking.getBookingId());

        // Update to CONFIRMED
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Notify UI via WebSocket
        bookingService.publishStatusChange(booking, oldStatus);

        // BACKGROUND ENRICHMENT: Calculate Map & Pricing and then set to PENDING
        try {
            bookingService.enrichAndReadyBooking(booking);
        } catch (Exception e) {
            log.error("✗ Failed to enrich bookingId={}: {}", booking.getBookingId(), e.getMessage());
        }
    }

    private void processPaymentFailure(Booking booking, String error) {
        log.warn("✗ Payment failed for bookingId={}: {}", booking.getBookingId(), error);

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.FAILED);
        booking.setCancelReason("Payment failed: " + error);
        booking.setUpdatedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // Notify UI via WebSocket
        bookingService.publishStatusChange(booking, oldStatus);
    }
}

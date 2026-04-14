package com.gomirai.ride.booking.messaging;

import com.gomirai.ride.booking.enums.BookingStatus;
import com.gomirai.ride.booking.model.Booking;
import com.gomirai.ride.booking.repository.BookingRepository;
import com.gomirai.ride.booking.service.BookingService;
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
        log.info("✓ Payment succeeded for bookingId={}. Triggering async enrichment...", booking.getBookingId());

        // Update to CONFIRMED in memory for visual state transition
        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setUpdatedAt(LocalDateTime.now());
        
        // We perform ONE save here to ensure the state is persisted as CONFIRMED 
        // before the async enrichment starts (which will eventually set it to PENDING)
        bookingRepository.save(booking);

        // Notify UI via WebSocket
        bookingService.publishStatusChange(booking, oldStatus);

        // ASYNC ENRICHMENT: The @Async method will handle the REST calls and final PENDING save
        bookingService.enrichAndReadyBooking(booking);
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

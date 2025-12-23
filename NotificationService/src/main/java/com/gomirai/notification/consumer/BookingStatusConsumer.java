package com.gomirai.notification.consumer;

import com.gomirai.common.dto.event.BookingStatusChangedEvent;
import com.gomirai.notification.service.WebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingStatusConsumer {
    
    private final WebSocketPushService webSocketPushService;
    
    @KafkaListener(
        topics = "${kafka.topic.booking-status-changed:booking.status.changed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleBookingStatusChanged(
            @Payload BookingStatusChangedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("=== [KAFKA RECEIVE] BookingStatusChangedEvent ===");
            log.info("bookingId={}, status={}, customerId={}", 
                event.getBookingId(), event.getStatus(), event.getCustomerId());
            
            // 1. Notify Customer
            if (event.getCustomerId() != null) {
                webSocketPushService.pushBookingStatus(event.getCustomerId(), event);
                log.info("✓ Pushed BOOKING_STATUS to customer {}", event.getCustomerId());
            }
            
            // 2. Notify Driver (if assigned)
            if (event.getDriverId() != null) {
                webSocketPushService.pushBookingStatus(event.getDriverId(), event);
                log.info("✓ Pushed BOOKING_STATUS to driver {}", event.getDriverId());
            }
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("✗ Error processing BookingStatusChangedEvent", e);
        }
    }
}

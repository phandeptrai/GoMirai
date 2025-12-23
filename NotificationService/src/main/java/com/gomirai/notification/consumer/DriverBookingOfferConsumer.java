package com.gomirai.notification.consumer;

import com.gomirai.common.dto.event.DriverBookingOfferEvent;
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
public class DriverBookingOfferConsumer {
    
    private final WebSocketPushService webSocketPushService;
    
    @KafkaListener(
        topics = "${kafka.topic.driver-booking-offer-notification:driver.booking.offer.notification}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDriverBookingOffer(
            @Payload DriverBookingOfferEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        long receiveTs = System.currentTimeMillis();
        try {
            log.info("=== [KAFKA RECEIVE] NotificationService ===");
            log.info("[KAFKA RECEIVE] timestamp={}, bookingId={}, driverId={}, userId={}", 
                receiveTs, event.getBookingId(), event.getDriverId(), event.getUserId());
            log.info("[KAFKA RECEIVE] key={}", key);
            
            if (event.getUserId() == null) {
                log.error("[KAFKA RECEIVE] ✗ userId is NULL, cannot push WebSocket");
                acknowledgment.acknowledge();
                return;
            }
            
            // Push to WebSocket with envelope { type, payload }
            log.info("[WS PUSH] Calling pushDriverOffer for userId={}", event.getUserId());
            webSocketPushService.pushDriverOffer(event.getUserId().toString(), event);
            
            acknowledgment.acknowledge();
            log.info("[KAFKA RECEIVE] ✓ Processed at {}, userId={}", receiveTs, event.getUserId());
        } catch (Exception e) {
            log.error("[KAFKA RECEIVE] ✗ Error processing event", e);
        }
    }
}

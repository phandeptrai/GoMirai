package com.gomirai.driver.messaging;

import com.gomirai.common.dto.event.DriverAcceptedEvent;
import com.gomirai.common.dto.event.DriverBookingOfferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer gửi các sự kiện liên quan đến tài xế và cuốc xe.
 * 
 * Topics:
 * - driver.accepted: Tài xế nhận cuốc → BookingService cập nhật booking
 * - driver.booking.offer.notification: Gửi offer đến NotificationService để
 * push WebSocket
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverBookingEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.driver-accepted:driver.accepted}")
    private String driverAcceptedTopic;

    @Value("${kafka.topic.driver-booking-offer-notification:driver.booking.offer.notification}")
    private String driverOfferNotificationTopic;

    /**
     * Publish sự kiện tài xế nhận cuốc
     * 
     * Consumer: BookingService (DriverEventsConsumer)
     * Mục đích: Atomic update booking status PENDING → MATCHED, gán driverId
     */
    public void publishDriverAccepted(DriverAcceptedEvent event) {
        try {
            kafkaTemplate.send(driverAcceptedTopic, event.getBookingId().toString(), event);
            log.info("Published DriverAcceptedEvent: bookingId={}, driverId={}",
                    event.getBookingId(), event.getDriverId());
        } catch (Exception e) {
            log.error("Failed to publish DriverAcceptedEvent for bookingId={}",
                    event.getBookingId(), e);
        }
    }

    /**
     * Publish sự kiện notification booking offer cho tài xế
     * 
     * Consumer: NotificationService (DriverBookingOfferConsumer)
     * Mục đích: Gửi WebSocket realtime "Cuốc mới!" cho driver app
     * 
     * Lưu ý: Event đã có userId (mapped từ driverId) để NotificationService push
     * đúng user
     */
    public void publishDriverOfferNotification(DriverBookingOfferEvent event) {
        try {
            long publishTs = System.currentTimeMillis();
            log.info("=== [KAFKA PUBLISH] Driver Offer Notification ===");
            log.info("[KAFKA PUBLISH] timestamp={}, bookingId={}, driverId={}, userId={}",
                    publishTs, event.getBookingId(), event.getDriverId(), event.getUserId());
            log.info("[KAFKA PUBLISH] topic={}, key={}", driverOfferNotificationTopic, event.getUserId());

            kafkaTemplate.send(driverOfferNotificationTopic, event.getUserId().toString(), event);

            log.info("[KAFKA PUBLISH] ✓ Event sent at {}", publishTs);
        } catch (Exception e) {
            log.error("[KAFKA PUBLISH] ✗ Failed to publish: bookingId={}",
                    event.getBookingId(), e);
        }
    }
}

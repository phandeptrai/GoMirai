package com.gomirai.tracking.messaging;

import com.gomirai.common.dto.event.DriverBookingOfferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer gửi offer cuốc xe đến DriverService.
 * 
 * Topic: driver.booking.offer
 * Consumer: DriverService (lưu offer vào repository)
 * 
 * Luồng: BookingSearchDriversEvent → Tìm tài xế gần → Gửi offer
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverBookingEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.driver-booking-offer:driver.booking.offer}")
    private String driverBookingOfferTopic;

    /**
     * Publish sự kiện gửi booking offer cho tài xế
     * 
     * Consumer: DriverService (DriverBookingOfferConsumer)
     * Mục đích: Lưu offer vào DB, sau đó DriverService publish notification cho
     * NotificationService
     */
    public void publishDriverBookingOffer(DriverBookingOfferEvent event) {
        try {
            kafkaTemplate.send(driverBookingOfferTopic, event.getDriverId().toString(), event);
            log.info("Published DriverBookingOfferEvent: bookingId={}, driverId={}",
                    event.getBookingId(), event.getDriverId());
        } catch (Exception e) {
            log.error("Failed to publish DriverBookingOfferEvent for bookingId={}, driverId={}",
                    event.getBookingId(), event.getDriverId(), e);
        }
    }
}

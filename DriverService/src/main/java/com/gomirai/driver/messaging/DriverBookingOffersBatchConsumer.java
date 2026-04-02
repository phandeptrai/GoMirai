package com.gomirai.driver.messaging;

import com.gomirai.common.dto.event.DriverBookingOffersBatchEvent;
import com.gomirai.driver.service.DriverBookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes one batched offer record from Tracking and delegates per-driver handling
 * (same DB + notification flow as single-offer path).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverBookingOffersBatchConsumer {

    private final DriverBookingService driverBookingService;

    @KafkaListener(
            topics = "${kafka.topic.driver-booking-offer-batch:driver.booking.offer.batch}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleBatch(
            @Payload DriverBookingOffersBatchEvent batch,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.debug("DriverService: batch offers bookingId={}, drivers={}",
                    batch.getBookingId(),
                    batch.getDriverIds() != null ? batch.getDriverIds().size() : 0);
            driverBookingService.handleDriverBookingOffersBatch(batch);
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DriverBookingOffersBatchEvent bookingId={}", batch.getBookingId(), e);
        }
    }
}

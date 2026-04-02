package com.gomirai.driver.messaging;

import com.gomirai.common.dto.event.DriverBookingOfferEvent;
import com.gomirai.driver.service.DriverBookingService;
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
    
    private final DriverBookingService driverBookingService;
    
    @KafkaListener(
        topics = "${kafka.topic.driver-booking-offer:driver.booking.offer}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDriverBookingOffer(
            @Payload DriverBookingOfferEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.debug("Received DriverBookingOfferEvent bookingId={}, driverId={}",
                event.getBookingId(), event.getDriverId());

            driverBookingService.handleDriverBookingOffer(event);

            acknowledgment.acknowledge();
            log.trace("Processed DriverBookingOfferEvent bookingId={}, driverId={}",
                event.getBookingId(), event.getDriverId());
        } catch (Exception e) {
            log.error("Error processing DriverBookingOfferEvent for bookingId={}, driverId={}",
                event.getBookingId(), event.getDriverId(), e);
            // Could implement retry logic here
        }
    }
}







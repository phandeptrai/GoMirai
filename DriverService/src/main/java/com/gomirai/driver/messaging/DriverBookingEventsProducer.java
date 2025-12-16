package com.gomirai.driver.messaging;

import com.gomirai.common.dto.event.DriverAcceptedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverBookingEventsProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.driver-accepted:driver.accepted}")
    private String driverAcceptedTopic;
    
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
}



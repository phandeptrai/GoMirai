package com.gomirai.tracking.consumer;

import com.gomirai.common.dto.event.DriverAcceptedEvent;
import com.gomirai.tracking.service.BookingSearchService;
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
public class DriverAcceptedConsumer {
    
    private final BookingSearchService bookingSearchService;
    
    @KafkaListener(
        topics = "${kafka.topic.driver-accepted:driver.accepted}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDriverAccepted(
            @Payload DriverAcceptedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received DriverAcceptedEvent: bookingId={}, driverId={}", 
                event.getBookingId(), event.getDriverId());
            
            // Xóa search state vì đã có tài xế nhận booking
            bookingSearchService.removeSearchState(event.getBookingId());
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DriverAcceptedEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
}






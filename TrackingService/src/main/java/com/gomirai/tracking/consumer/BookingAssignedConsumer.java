package com.gomirai.tracking.consumer;

import com.gomirai.common.dto.event.BookingAssignedEvent;
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
public class BookingAssignedConsumer {
    
    private final BookingSearchService bookingSearchService;
    
    @KafkaListener(
        topics = "${kafka.topic.booking-assigned:booking.assigned}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleBookingAssigned(
            @Payload BookingAssignedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received BookingAssignedEvent: bookingId={}, driverId={}", 
                event.getBookingId(), event.getDriverId());
            
            // Xóa search state vì booking đã được assigned (driver đã nhận)
            bookingSearchService.removeSearchState(event.getBookingId());
            log.info("✓ Removed search state for assigned booking: {}", event.getBookingId());
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing BookingAssignedEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
}

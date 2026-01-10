package com.gomirai.tracking.consumer;

import com.gomirai.common.dto.event.BookingCanceledEvent;
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
public class BookingCanceledConsumer {
    
    private final BookingSearchService bookingSearchService;
    
    @KafkaListener(
        topics = "${kafka.topic.booking-canceled:booking-canceled-event}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleBookingCanceled(
            @Payload BookingCanceledEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received BookingCanceledEvent: bookingId={}, reason={}", 
                event.getBookingId(), event.getReason());
            
            // Xóa search state vì booking đã bị hủy
            bookingSearchService.removeSearchState(event.getBookingId());
            log.info("✓ Removed search state for canceled booking: {}", event.getBookingId());
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing BookingCanceledEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
}

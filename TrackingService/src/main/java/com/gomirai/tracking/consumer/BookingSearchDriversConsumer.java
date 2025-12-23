package com.gomirai.tracking.consumer;

import com.gomirai.common.dto.event.BookingSearchDriversEvent;
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
public class BookingSearchDriversConsumer {
    
    private final BookingSearchService bookingSearchService;
    
    @KafkaListener(
        topics = "${kafka.topic.booking-search-drivers:booking.search_drivers}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleBookingSearchDrivers(
            @Payload BookingSearchDriversEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received BookingSearchDriversEvent: bookingId={}, vehicleType={}, radius={}m", 
                event.getBookingId(), event.getVehicleType(), event.getRadiusMeters());
            
            bookingSearchService.handleBookingSearchDrivers(event);
            
            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing BookingSearchDriversEvent for bookingId={}", 
                event.getBookingId(), e);
            // Could implement retry logic here
        }
    }
}







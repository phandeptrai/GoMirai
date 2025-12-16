package com.gomirai.booking.messaging;

import com.gomirai.booking.event.BookingAssignedEvent;
import com.gomirai.booking.event.BookingCompletedEvent;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventsProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topic.booking-search-drivers:booking.search_drivers}")
    private String searchDriversTopic;
    
    @Value("${kafka.topic.booking-assigned:booking.assigned}")
    private String assignedTopic;
    
    @Value("${kafka.topic.booking-completed:booking.completed}")
    private String completedTopic;
    
    public void publishSearchDriversEvent(BookingSearchDriversEvent event) {
        try {
            kafkaTemplate.send(searchDriversTopic, event.getBookingId().toString(), event);
            log.info("Published BookingSearchDriversEvent: bookingId={}, vehicleType={}", 
                event.getBookingId(), event.getVehicleType());
        } catch (Exception e) {
            log.error("Failed to publish BookingSearchDriversEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
    
    public void publishBookingAssignedEvent(BookingAssignedEvent event) {
        try {
            kafkaTemplate.send(assignedTopic, event.getBookingId().toString(), event);
            log.info("Published BookingAssignedEvent: bookingId={}, driverId={}", 
                event.getBookingId(), event.getDriverId());
        } catch (Exception e) {
            log.error("Failed to publish BookingAssignedEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
    
    public void publishBookingCompletedEvent(BookingCompletedEvent event) {
        try {
            kafkaTemplate.send(completedTopic, event.getBookingId().toString(), event);
            log.info("Published BookingCompletedEvent: bookingId={}, finalAmount={}", 
                event.getBookingId(), event.getFinalAmount());
        } catch (Exception e) {
            log.error("Failed to publish BookingCompletedEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
}



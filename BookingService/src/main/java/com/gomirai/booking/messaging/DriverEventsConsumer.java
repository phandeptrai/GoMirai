package com.gomirai.booking.messaging;

import com.gomirai.common.dto.event.DriverAcceptedEvent;
import com.gomirai.common.dto.event.DriverDeclinedEvent;
import com.gomirai.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer xử lý các sự kiện từ tài xế.
 * 
 * Topics lắng nghe:
 * - driver.accepted: Tài xế nhận cuốc → cập nhật booking thành MATCHED
 * - driver.declined: Tài xế từ chối cuốc → có thể tìm tài xế khác
 * 
 * Producer: DriverService (khi tài xế accept/decline qua REST hoặc WebSocket)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverEventsConsumer {

    private final BookingService bookingService;

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), dltStrategy = DltStrategy.NO_DLT)
    @KafkaListener(topics = "${kafka.topic.driver-accepted:driver.accepted}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void handleDriverAccepted(
            @Payload DriverAcceptedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received DriverAcceptedEvent: bookingId={}, driverId={}",
                    event.getBookingId(), event.getDriverId());

            bookingService.handleDriverAccepted(event);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DriverAcceptedEvent for bookingId={}",
                    event.getBookingId(), e);
            throw e; // Throw to trigger retry
        }
    }

    @RetryableTopic(attempts = "3", backoff = @Backoff(delay = 2000, multiplier = 2.0), dltStrategy = DltStrategy.NO_DLT)
    @KafkaListener(topics = "${kafka.topic.driver-declined:driver.declined}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void handleDriverDeclined(
            @Payload DriverDeclinedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {
        try {
            log.info("Received DriverDeclinedEvent: bookingId={}, driverId={}",
                    event.getBookingId(), event.getDriverId());

            bookingService.handleDriverDeclined(event);

            acknowledgment.acknowledge();
        } catch (Exception e) {
            log.error("Error processing DriverDeclinedEvent for bookingId={}",
                    event.getBookingId(), e);
            throw e; // Throw to trigger retry
        }
    }
}

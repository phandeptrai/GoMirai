package com.gomirai.booking.messaging;

import com.gomirai.common.dto.event.BookingAssignedEvent;
import com.gomirai.common.dto.event.BookingCompletedEvent;
import com.gomirai.common.dto.event.BookingCanceledEvent;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.common.dto.event.RefundRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka Producer gửi các sự kiện liên quan đến Booking.
 * 
 * Topics:
 * - booking.search_drivers: Yêu cầu tìm tài xế cho booking mới
 * - booking.assigned: Booking đã được gán cho tài xế
 * - booking.completed: Booking hoàn thành
 * - booking-canceled-event: Booking bị hủy
 * - booking.status.changed: Trạng thái booking thay đổi (cho
 * NotificationService)
 * - refund.requested: Yêu cầu hoàn tiền khi hủy booking đã thanh toán ví
 * 
 * Consumers:
 * - TrackingService: booking.search_drivers, booking.assigned
 * - DriverService: booking.search_drivers (gửi offer cho tài xế)
 * - NotificationService: booking.status.changed
 * - PaymentService: refund.requested
 */
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

    @Value("${kafka.topic.booking-canceled:booking-canceled-event}")
    private String canceledTopic;

    @Value("${kafka.topic.booking-status-changed:booking.status.changed}")
    private String statusChangedTopic;

    @Value("${kafka.topic.refund-requested:refund.requested}")
    private String refundRequestedTopic;

    public void publishBookingStatusChangedEvent(com.gomirai.common.dto.event.BookingStatusChangedEvent event) {
        try {
            kafkaTemplate.send(statusChangedTopic, event.getBookingId().toString(), event);
            log.info("Published BookingStatusChangedEvent: bookingId={}, status={}, customerId={}",
                    event.getBookingId(), event.getStatus(), event.getCustomerId());
        } catch (Exception e) {
            log.error("Failed to publish BookingStatusChangedEvent for bookingId={}",
                    event.getBookingId(), e);
        }
    }

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

    public void publishBookingCanceledEvent(BookingCanceledEvent event) {
        try {
            kafkaTemplate.send(canceledTopic, event.getBookingId().toString(), event);
            log.info("Published BookingCanceledEvent: bookingId={}, reason={}, canceledBy={}",
                    event.getBookingId(), event.getReason(), event.getCanceledBy());
        } catch (Exception e) {
            log.error("Failed to publish BookingCanceledEvent for bookingId={}",
                    event.getBookingId(), e);
        }
    }

    /**
     * Publish refund request event to PaymentService
     * This is used when a booking paid with WALLET is canceled
     */
    public void publishRefundRequestedEvent(RefundRequestedEvent event) {
        try {
            kafkaTemplate.send(refundRequestedTopic, event.getBookingId().toString(), event);
            log.info("Published RefundRequestedEvent: bookingId={}, customerId={}, amount={}",
                    event.getBookingId(), event.getCustomerId(), event.getAmount());
        } catch (Exception e) {
            log.error("Failed to publish RefundRequestedEvent for bookingId={}",
                    event.getBookingId(), e);
        }
    }
}

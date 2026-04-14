package com.gomirai.ride.booking.messaging;

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

    @Value("${kafka.topic.booking-created:booking-created-event}")
    private String createdTopic;

    /**
     * Publish sự kiện tạo booking mới để kích hoạt luồng Saga (Async)
     * 
     * Consumers:
     * 1. MapService & PricingService: Để làm giàu dữ liệu quãng đường và giá
     * (Async)
     * 2. PaymentService: Để thực hiện trừ tiền ví (WALLET)
     */
    public void publishBookingCreatedEvent(com.gomirai.common.dto.event.BookingCreatedEvent event) {
        kafkaTemplate.send(createdTopic, event.getBookingId().toString(), event)
            .whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("✓ Published BookingCreatedEvent: bookingId={}, topic={}", 
                        event.getBookingId(), createdTopic);
                } else {
                    log.error("✗ Failed to publish BookingCreatedEvent for bookingId={}: {}", 
                        event.getBookingId(), ex.getMessage());
                }
            });
    }

    /**
     * Publish sự kiện thay đổi trạng thái booking
     * 
     * Consumer: NotificationService (BookingStatusConsumer)
     * Mục đích: Gửi thông báo real-time cho customer/driver qua WebSocket
     */
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

    /**
     * Publish sự kiện tìm tài xế cho booking mới
     * 
     * Consumers:
     * 1. TrackingService (BookingSearchDriversConsumer) - Tìm tài xế gần điểm đón
     * 2. DriverService (BookingSearchDriversConsumer) - Gửi booking offer cho tài
     * xế
     */
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

    /**
     * Publish sự kiện booking đã được gán cho tài xế
     * 
     * Consumer: TrackingService (BookingAssignedConsumer)
     * Mục đích: Dừng tìm tài xế, cập nhật trạng thái tracking
     */
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

    /**
     * Publish sự kiện booking hoàn thành
     * 
     * Consumer: PaymentService (BookingCompletedConsumer)
     * Mục đích: Xử lý thanh toán cuối cùng, trả tiền cho tài xế (nếu WALLET)
     */
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

    /**
     * Publish sự kiện booking bị hủy
     * 
     * Consumer: TrackingService (BookingCanceledConsumer)
     * Mục đích: Dừng tìm tài xế, cleanup tracking state
     */
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
     * Publish sự kiện yêu cầu hoàn tiền
     * 
     * Consumer: PaymentService (RefundKafkaConsumer)
     * Mục đích: Hoàn tiền vào ví customer khi hủy booking đã thanh toán WALLET
     * 
     * Trigger: Khi booking thanh toán bằng WALLET bị hủy hoặc không tìm được tài xế
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

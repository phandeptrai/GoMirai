package com.gomirai.communication.review.messaging;

import com.gomirai.common.dto.event.DriverRatingUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Kafka Producer để publish event khi rating của driver được cập nhật.
 * 
 * Luồng:
 * 1. Customer đánh giá driver
 * 2. ReviewService lưu review
 * 3. ReviewService tính rating trung bình
 * 4. Publish DriverRatingUpdatedEvent
 * 5. DriverService consume và cập nhật rating trong profile
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverRatingEventsProducer {

        private final KafkaTemplate<String, Object> kafkaTemplate;

        @Value("${kafka.topic.driver-rating-updated:driver.rating.updated}")
        private String driverRatingUpdatedTopic;

        /**
         * Publish sự kiện rating driver được cập nhật
         * 
         * Consumer: DriverService (DriverRatingUpdatedConsumer)
         * Mục đích: Cập nhật averageRating và totalReviews trong DriverProfile
         * 
         * Trigger: Sau khi customer đánh giá driver, ReviewService tính lại rating
         * trung bình
         */
        public void publishDriverRatingUpdated(UUID driverUserId, Double averageRating,
                        Integer totalReviews, UUID bookingId) {
                DriverRatingUpdatedEvent event = DriverRatingUpdatedEvent.builder()
                                .driverUserId(driverUserId)
                                .averageRating(averageRating)
                                .totalReviews(totalReviews)
                                .bookingId(bookingId)
                                .build();

                log.info("Publishing DriverRatingUpdatedEvent: driverUserId={}, avgRating={}, totalReviews={}",
                                driverUserId, averageRating, totalReviews);

                kafkaTemplate.send(driverRatingUpdatedTopic, driverUserId.toString(), event);

                log.info("✓ Published DriverRatingUpdatedEvent for driver {}", driverUserId);
        }
}

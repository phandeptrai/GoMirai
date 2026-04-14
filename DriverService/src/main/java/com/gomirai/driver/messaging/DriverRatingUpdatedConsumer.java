package com.gomirai.driver.messaging;

import com.gomirai.common.dto.event.DriverRatingUpdatedEvent;
import com.gomirai.driver.model.DriverProfile;
import com.gomirai.driver.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Kafka Consumer xử lý sự kiện cập nhật rating của driver.
 * 
 * Topic: driver.rating.updated
 * Producer: ReviewService
 * 
 * Luồng:
 * 1. Customer đánh giá driver (ReviewService)
 * 2. ReviewService tính rating trung bình
 * 3. ReviewService publish DriverRatingUpdatedEvent
 * 4. DriverService consume event này
 * 5. Cập nhật rating trong DriverProfile
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverRatingUpdatedConsumer {

    private final DriverProfileRepository driverProfileRepository;

    @KafkaListener(topics = "${kafka.topic.driver-rating-updated:driver.rating.updated}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void handleDriverRatingUpdated(
            @Payload DriverRatingUpdatedEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            Acknowledgment acknowledgment) {

        try {
            log.debug("DriverRatingUpdatedEvent driverUserId={}, avgRating={}, totalReviews={}",
                    event.getDriverUserId(), event.getAverageRating(), event.getTotalReviews());

            // Tìm driver profile bằng userId
            Optional<DriverProfile> profileOpt = driverProfileRepository.findByUserId(event.getDriverUserId());

            if (profileOpt.isEmpty()) {
                // Defensive: some legacy publishers might send driverId instead of userId.
                profileOpt = driverProfileRepository.findById(event.getDriverUserId());
            }

            if (profileOpt.isEmpty()) {
                log.warn("✗ Driver profile not found for id (userId/driverId): {}", event.getDriverUserId());
                acknowledgment.acknowledge();
                return;
            }

            DriverProfile profile = profileOpt.get();

            // Cập nhật rating và số chuyến (nếu có)
            Double oldRating = profile.getRating();
            profile.setRating(event.getAverageRating());
            profile.setCompletedTrips(event.getTotalReviews()); // Số review ~ số chuyến được đánh giá
            profile.setUpdatedAt(Instant.now());

            driverProfileRepository.save(profile);

            log.debug("Updated driver rating: {} -> {} (driverId: {})",
                    oldRating, event.getAverageRating(), profile.getDriverId());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("✗ Error processing DriverRatingUpdatedEvent", e);
            // Don't acknowledge - let Kafka retry
        }
    }
}

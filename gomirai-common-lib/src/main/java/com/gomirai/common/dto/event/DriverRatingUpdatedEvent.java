package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event được publish khi rating của driver thay đổi.
 * 
 * Producer: ReviewService (sau khi customer đánh giá)
 * Consumer: DriverService (cập nhật rating trung bình)
 * 
 * Topic: driver.rating.updated
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverRatingUpdatedEvent {

    /**
     * userId của driver (dùng để lookup trong DriverService)
     */
    private UUID driverUserId;

    /**
     * Rating trung bình mới (đã tính toán)
     */
    private Double averageRating;

    /**
     * Tổng số review
     */
    private Integer totalReviews;

    /**
     * bookingId của chuyến vừa được đánh giá
     */
    private UUID bookingId;
}

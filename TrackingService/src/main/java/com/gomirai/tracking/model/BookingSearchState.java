package com.gomirai.tracking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Model để lưu trạng thái tìm kiếm tài xế cho booking
 * Lưu trong Redis để có thể tăng bán kính và hủy booking sau 15 phút
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSearchState {
    private UUID bookingId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String vehicleType;
    private Double currentRadiusMeters; // Bán kính hiện tại
    private Integer searchAttempts; // Số lần đã tìm kiếm
    private LocalDateTime searchStartTime; // Thời gian bắt đầu tìm kiếm
    private LocalDateTime lastSearchTime; // Thời gian tìm kiếm lần cuối
    private Boolean isActive; // Trạng thái đang tìm kiếm hay không
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
    private String pickupAddress;
    private String dropoffAddress;

    // Track drivers who have already been notified to avoid duplicate offers
    @Builder.Default
    private Set<String> notifiedDriverIds = new HashSet<>();
}

package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BookingSearchDriversEvent extends BaseEvent {
    private UUID bookingId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String vehicleType;
    private Double radiusMeters;
    private String requestedAt; // ISO8601
    
    // Additional booking info to avoid extra API calls
    private String pickupAddress;
    private String dropoffAddress;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
    
    public BookingSearchDriversEvent(UUID bookingId, Double pickupLatitude, Double pickupLongitude, 
                                   String vehicleType, Double radiusMeters) {
        super();
        init("BookingSearchDriversEvent", "BookingService");
        this.bookingId = bookingId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.vehicleType = vehicleType;
        this.radiusMeters = radiusMeters;
        this.requestedAt = java.time.Instant.now().toString();
    }
    
    // Full constructor with all booking details
    public BookingSearchDriversEvent(UUID bookingId, Double pickupLatitude, Double pickupLongitude,
                                   Double dropoffLatitude, Double dropoffLongitude,
                                   String vehicleType, Double radiusMeters,
                                   String pickupAddress, String dropoffAddress,
                                   Double estimatedDistanceKm, Integer estimatedDurationMinutes,
                                   Double estimatedFare, String currency) {
        super();
        init("BookingSearchDriversEvent", "BookingService");
        this.bookingId = bookingId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.dropoffLatitude = dropoffLatitude;
        this.dropoffLongitude = dropoffLongitude;
        this.vehicleType = vehicleType;
        this.radiusMeters = radiusMeters;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.estimatedFare = estimatedFare;
        this.currency = currency;
        this.requestedAt = java.time.Instant.now().toString();
    }
}







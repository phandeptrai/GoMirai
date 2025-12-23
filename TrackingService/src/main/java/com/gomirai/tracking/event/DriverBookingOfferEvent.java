package com.gomirai.tracking.event;

import com.gomirai.common.dto.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DriverBookingOfferEvent extends BaseEvent {
    private UUID bookingId;
    private UUID driverId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String vehicleType;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
    private String pickupAddress;
    private String dropoffAddress;
    private String offeredAt; // ISO8601
    
    public DriverBookingOfferEvent(UUID bookingId, UUID driverId, 
                                   Double pickupLatitude, Double pickupLongitude,
                                   Double dropoffLatitude, Double dropoffLongitude,
                                   String vehicleType, Double estimatedDistanceKm,
                                   Integer estimatedDurationMinutes, Double estimatedFare,
                                   String currency, String pickupAddress, String dropoffAddress) {
        super();
        init("DriverBookingOfferEvent", "TrackingService");
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.dropoffLatitude = dropoffLatitude;
        this.dropoffLongitude = dropoffLongitude;
        this.vehicleType = vehicleType;
        this.estimatedDistanceKm = estimatedDistanceKm;
        this.estimatedDurationMinutes = estimatedDurationMinutes;
        this.estimatedFare = estimatedFare;
        this.currency = currency;
        this.pickupAddress = pickupAddress;
        this.dropoffAddress = dropoffAddress;
        this.offeredAt = java.time.Instant.now().toString();
    }
}







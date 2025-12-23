package com.gomirai.driver.event;

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
}







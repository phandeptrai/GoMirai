package com.gomirai.tracking.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingInfoResponse {
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;
    private String pickupAddress;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
}







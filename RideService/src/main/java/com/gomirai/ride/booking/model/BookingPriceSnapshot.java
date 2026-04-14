package com.gomirai.ride.booking.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingPriceSnapshot {
    private Double baseFare;
    private Double distanceFare;
    private Double timeFare;
    private Double surgeMultiplier;
    private Double discount;
    private Double finalAmount;
    private String currency;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private String pricingRuleId;
}


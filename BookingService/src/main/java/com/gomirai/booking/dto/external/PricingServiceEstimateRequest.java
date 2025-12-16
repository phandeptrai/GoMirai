package com.gomirai.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingServiceEstimateRequest {
    private String vehicleType;
    private double distanceKm;
    private int durationMinute;
    private String region;
}



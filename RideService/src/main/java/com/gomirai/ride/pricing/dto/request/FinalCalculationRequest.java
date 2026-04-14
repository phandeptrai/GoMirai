package com.gomirai.ride.pricing.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class FinalCalculationRequest {
    @NotBlank(message = "rideId is required")
    private String rideId;

    @NotBlank
    private String vehicleType;

    @Positive
    private double actualDistanceKm;

    @Positive
    private int actualDurationMinute;

    @NotBlank
    private String region;
}
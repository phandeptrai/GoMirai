package com.gomirai.pricing.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class EstimateRequest {
    @NotBlank(message = "vehicleType is required")
    private String vehicleType;

    @Positive(message = "distanceKm must be > 0")
    private double distanceKm;

    @Positive(message = "durationMinute must be > 0")
    private int durationMinute;

    @NotBlank(message = "region is required")
    private String region;
}

package com.gomirai.booking.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteBookingRequest {
    
    @Positive(message = "Actual distance must be positive")
    private Double actualDistanceKm;
    
    @Positive(message = "Actual duration must be positive")
    private Integer actualDurationMinutes;
}



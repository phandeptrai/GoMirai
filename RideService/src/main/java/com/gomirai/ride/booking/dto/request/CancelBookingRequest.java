package com.gomirai.ride.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancelBookingRequest {
    
    @NotBlank(message = "Cancel reason is required")
    private String reason;
}



package com.gomirai.booking.dto.request;

import com.gomirai.booking.model.AddressSnapshot;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.booking.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    
    @NotNull(message = "Pickup location is required")
    @Valid
    private AddressSnapshot pickupLocation;
    
    @NotNull(message = "Dropoff location is required")
    @Valid
    private AddressSnapshot dropoffLocation;
    
    @NotNull(message = "Vehicle type is required")
    private VehicleType vehicleType;
    
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;
    
    private LocalDateTime scheduledAt; // Nullable for immediate booking
    
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;
    
    private String idempotencyKey; // For idempotent creation
}



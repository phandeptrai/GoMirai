package com.gomirai.ride.booking.dto.response;

import com.gomirai.ride.booking.enums.BookingStatus;
import com.gomirai.ride.booking.enums.PaymentMethod;
import com.gomirai.ride.booking.model.AddressSnapshot;
import com.gomirai.ride.booking.model.BookingPriceSnapshot;
import com.gomirai.common.enums.VehicleType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingResponse {
    private UUID bookingId;
    private UUID customerId;
    private UUID driverId;
    private BookingStatus status;
    private AddressSnapshot pickupLocation;
    private AddressSnapshot dropoffLocation;
    private VehicleType vehicleType;
    private PaymentMethod paymentMethod;
    private BookingPriceSnapshot price;
    private Double estimatedDistanceKm;
    private Double actualDistanceKm;
    private Integer estimatedDurationMinutes;
    private Integer actualDurationMinutes;
    private String routePolyline;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime scheduledAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime actualPickupTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime actualDropoffTime;
    
    private UUID paymentId;
    private String cancelReason;
    private String notes;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
}



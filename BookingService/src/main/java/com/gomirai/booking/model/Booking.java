package com.gomirai.booking.model;

import com.gomirai.booking.enums.BookingStatus;
import com.gomirai.booking.enums.PaymentMethod;
import com.gomirai.common.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "bookings")
public class Booking {
    
    @Id
    private UUID bookingId;
    
    @Indexed
    private UUID customerId;
    
    @Indexed
    private UUID driverId; // Nullable until matched
    
    @Indexed
    private BookingStatus status;
    
    private AddressSnapshot pickupLocation;
    private AddressSnapshot dropoffLocation;
    private VehicleType vehicleType;
    private PaymentMethod paymentMethod;
    private BookingPriceSnapshot price;
    
    // Trip details
    private Double estimatedDistanceKm;
    private Double actualDistanceKm;
    private Integer estimatedDurationMinutes;
    private Integer actualDurationMinutes;
    private String routePolyline; // From Map Service
    
    // Timestamps
    private LocalDateTime scheduledAt; // Nullable for immediate booking
    private LocalDateTime actualPickupTime;
    private LocalDateTime actualDropoffTime;
    
    // Payment
    @Indexed
    private UUID paymentId; // Nullable until payment created
    
    // Metadata
    private String cancelReason;
    private String notes;
    private String idempotencyKey; // For idempotent creation
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    // Helper methods
    public boolean canBeCanceled() {
        return status == BookingStatus.CREATED 
            || status == BookingStatus.PENDING_PAYMENT 
            || status == BookingStatus.CONFIRMED
            || status == BookingStatus.PENDING 
            || status == BookingStatus.MATCHED;
    }
    
    public boolean isCompleted() {
        return status == BookingStatus.COMPLETED;
    }
    
    public boolean isActive() {
        return status == BookingStatus.MATCHED 
            || status == BookingStatus.DRIVER_ARRIVED 
            || status == BookingStatus.IN_PROGRESS;
    }
}


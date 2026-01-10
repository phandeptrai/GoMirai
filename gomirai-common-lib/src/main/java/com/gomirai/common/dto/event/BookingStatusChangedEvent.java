package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Event published when booking status changes
 * For real-time updates to customer via WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingStatusChangedEvent {
    private UUID bookingId;
    private String customerId;  // userId of customer - for WebSocket push
    private String driverId;    // userId of driver (optional)
    private String status;      // New status: MATCHED, DRIVER_ARRIVED, IN_PROGRESS, COMPLETED, CANCELED
    private String previousStatus;
    
    // Booking details for frontend display
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String pickupAddress;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String dropoffAddress;
    private Double estimatedFare;
    private String vehicleType;
}

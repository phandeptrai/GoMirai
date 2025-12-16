package com.gomirai.booking.event;

import com.gomirai.common.dto.event.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BookingSearchDriversEvent extends BaseEvent {
    private UUID bookingId;
    private Double pickupLatitude;
    private Double pickupLongitude;
    private String vehicleType;
    private Double radiusMeters;
    private String requestedAt; // ISO8601
    
    public BookingSearchDriversEvent(UUID bookingId, Double pickupLatitude, Double pickupLongitude, 
                                   String vehicleType, Double radiusMeters) {
        super();
        init("BookingSearchDriversEvent", "BookingService");
        this.bookingId = bookingId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.vehicleType = vehicleType;
        this.radiusMeters = radiusMeters;
        this.requestedAt = java.time.Instant.now().toString();
    }
}



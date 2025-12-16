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
public class DriverDeclinedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID driverId;
    private String declinedAt; // ISO8601
    private String reason; // Optional
    
    public DriverDeclinedEvent(UUID bookingId, UUID driverId, String reason) {
        super();
        init("DriverDeclinedEvent", "DriverService");
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.reason = reason;
        this.declinedAt = java.time.Instant.now().toString();
    }
}



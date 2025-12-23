package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DriverAcceptedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID driverId;
    private String acceptedAt; // ISO8601
    private Integer etaSeconds;
    
    public DriverAcceptedEvent(UUID bookingId, UUID driverId, Integer etaSeconds) {
        super();
        init("DriverAcceptedEvent", "DriverService");
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.etaSeconds = etaSeconds;
        this.acceptedAt = java.time.Instant.now().toString();
    }
}







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
public class BookingAssignedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;
    private UUID driverId;
    private String assignedAt; // ISO8601
    
    public BookingAssignedEvent(UUID bookingId, UUID customerId, UUID driverId) {
        super();
        init("BookingAssignedEvent", "BookingService");
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.assignedAt = java.time.Instant.now().toString();
    }
}

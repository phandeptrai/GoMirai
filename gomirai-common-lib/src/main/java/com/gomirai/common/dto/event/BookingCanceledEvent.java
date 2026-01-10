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
public class BookingCanceledEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;
    private String reason;
    private String canceledAt; // ISO8601
    private String canceledBy; // "CUSTOMER" or "SYSTEM"
    
    public BookingCanceledEvent(UUID bookingId, UUID customerId, String reason, String canceledBy) {
        super();
        init("BookingCanceledEvent", "BookingService");
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.reason = reason;
        this.canceledBy = canceledBy;
        this.canceledAt = java.time.Instant.now().toString();
    }
}

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
public class BookingCompletedEvent extends BaseEvent {
    private UUID bookingId;
    private UUID customerId;
    private UUID driverId;
    private Double finalAmount;
    private String currency;
    private String completedAt; // ISO8601
    
    public BookingCompletedEvent(UUID bookingId, UUID customerId, UUID driverId, 
                                Double finalAmount, String currency) {
        super();
        init("BookingCompletedEvent", "BookingService");
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.finalAmount = finalAmount;
        this.currency = currency;
        this.completedAt = java.time.Instant.now().toString();
    }
}



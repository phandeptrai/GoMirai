package com.gomirai.common.dto.event;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published when a refund is requested (e.g., when booking is canceled)
 * PaymentService will consume this event and process the refund asynchronously
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RefundRequestedEvent extends BaseEvent {

    private UUID bookingId;
    private UUID customerId;
    private BigDecimal amount;
    private String currency;
    private String reason;

    /**
     * Constructor with auto-initialization of base event fields
     */
    public RefundRequestedEvent(UUID bookingId, UUID customerId, BigDecimal amount, String currency, String reason) {
        super();
        init("RefundRequestedEvent", "BookingService");
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
    }
}

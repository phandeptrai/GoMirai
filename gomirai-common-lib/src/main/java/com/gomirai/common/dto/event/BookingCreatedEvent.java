package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookingCreatedEvent {
    private UUID bookingId;
    private UUID customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String vehicleType;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropoffLat;
    private Double dropoffLng;
}

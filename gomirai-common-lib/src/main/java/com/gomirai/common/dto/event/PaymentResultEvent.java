package com.gomirai.common.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentResultEvent {
    private UUID bookingId;
    private UUID customerId;
    private String status; // SUCCESS or FAILED
    private String transactionId;
    private String errorCode;
    private String errorMessage;
}

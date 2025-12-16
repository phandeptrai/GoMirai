package com.gomirai.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PricingServiceResponse {
    private long estimatedFare;
    private UUID appliedRuleId;
}



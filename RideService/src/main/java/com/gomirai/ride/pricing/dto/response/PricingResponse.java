package com.gomirai.ride.pricing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PricingResponse {
    private long estimatedFare; // dùng chung cho estimate & final
    private UUID appliedRuleId;
}
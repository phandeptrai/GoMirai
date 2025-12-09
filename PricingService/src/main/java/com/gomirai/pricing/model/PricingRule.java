package com.gomirai.pricing.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@Document(collection = "pricing_rules")
public class PricingRule {
    @Id
    private UUID ruleId;

    private String vehicleType; // MOTORBIKE, CAR_4_SEAT, CAR_7_SEAT...
    private double baseFare;
    private double perKmRate;
    private double perMinuteRate;
    private double surgeMultiplier = 1.0;
    private String region; // HCM, HN...
    private boolean active = true;

}
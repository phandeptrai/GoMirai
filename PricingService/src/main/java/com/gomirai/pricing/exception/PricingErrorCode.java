package com.gomirai.pricing.exception;

public final class PricingErrorCode {
    public static final String INVALID_VEHICLE_TYPE = "INVALID_VEHICLE_TYPE";
    public static final String INVALID_DISTANCE = "INVALID_DISTANCE";
    public static final String INVALID_DURATION = "INVALID_DURATION";
    public static final String INVALID_REGION = "INVALID_REGION";
    public static final String INVALID_RIDE_ID = "INVALID_RIDE_ID";
    public static final String PRICING_RULE_NOT_FOUND = "PRICING_RULE_NOT_FOUND";
    public static final String POSSIBLE_CHEAT_DISTANCE = "POSSIBLE_CHEAT_DISTANCE";
    public static final String POSSIBLE_CHEAT_DURATION = "POSSIBLE_CHEAT_DURATION";

    private PricingErrorCode() {
    }
}
package com.gomirai.tracking.dto;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriverLocationResponse {
    private String driverId;
    private double latitude;
    private double longitude;
    private DriverAvailabilityStatus status;
    private VehicleType vehicleType;
    private double distance; // Distance from search center (in meters)
    private long lastUpdatedAt;
    private Object details; // Optional enriched profile details
}

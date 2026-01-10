package com.gomirai.tracking.dto;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import lombok.Data;

@Data
public class NearbyDriverRequest {
    private double latitude;
    private double longitude;
    private double radius; // in meters
    private VehicleType vehicleType;
    private DriverAvailabilityStatus status; // Optional, default to ONLINE/AVAILABLE
    private int limit = 10;
}

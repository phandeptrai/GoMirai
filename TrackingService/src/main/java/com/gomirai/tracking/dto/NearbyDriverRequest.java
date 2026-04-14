package com.gomirai.tracking.dto;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NearbyDriverRequest {
    private double latitude;
    private double longitude;
    private double radius; // in meters
    private VehicleType vehicleType;
    private DriverAvailabilityStatus status; // Optional, default to ONLINE/AVAILABLE
    @Builder.Default
    private int limit = 10;
    @Builder.Default
    private boolean enrichDetails = false;
}

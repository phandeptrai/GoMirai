package com.gomirai.tracking.model;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverGeoState {
    private String driverId;
    private double latitude;
    private double longitude;
    private DriverAvailabilityStatus status;
    private VehicleType vehicleType;
    private long lastUpdatedAt;
}

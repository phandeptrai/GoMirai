package com.gomirai.booking.dto.external;

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
public class DriverLocationResponse {
    private String driverId;
    private Double latitude;
    private Double longitude;
    private DriverAvailabilityStatus status;
    private VehicleType vehicleType;
    private Double distance; // Distance in meters
    private Long lastUpdatedAt;
}






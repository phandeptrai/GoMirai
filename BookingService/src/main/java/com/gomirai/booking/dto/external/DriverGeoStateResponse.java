package com.gomirai.booking.dto.external;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverGeoStateResponse {
    private String driverId;
    private Double latitude;
    private Double longitude;
    private DriverAvailabilityStatus status;
    private VehicleType vehicleType;
    private Long lastUpdatedAt;
}






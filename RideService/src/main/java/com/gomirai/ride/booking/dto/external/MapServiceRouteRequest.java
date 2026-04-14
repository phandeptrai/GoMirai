package com.gomirai.ride.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapServiceRouteRequest {
    private GeoPoint origin;
    private GeoPoint destination;
    private String profile = "driving";
}



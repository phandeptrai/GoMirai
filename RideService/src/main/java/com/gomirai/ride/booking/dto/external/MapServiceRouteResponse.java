package com.gomirai.ride.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MapServiceRouteResponse {
    private Double distance;           // meters
    private Integer duration;         // seconds
    private List<GeoPoint> geometry;
    private String polyline;
    private List<RouteStep> steps;
}



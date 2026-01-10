package com.gomirai.booking.dto.external;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStep {
    private GeoPoint location;
    private String instruction;
    private Double distance;
    private Integer duration;
}



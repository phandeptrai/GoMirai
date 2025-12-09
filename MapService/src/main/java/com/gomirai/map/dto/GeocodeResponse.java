package com.gomirai.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeocodeResponse {
    private GeoPoint location;
    private String formattedAddress;
}




package com.gomirai.ride.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodeRequest {
    
    @NotNull(message = "Location is required")
    @Valid
    private GeoPoint location;
}







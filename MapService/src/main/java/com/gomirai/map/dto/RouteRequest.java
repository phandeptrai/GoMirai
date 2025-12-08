package com.gomirai.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteRequest {
    
    @NotNull(message = "Origin is required")
    @Valid
    private GeoPoint origin;
    
    @NotNull(message = "Destination is required")
    @Valid
    private GeoPoint destination;
    
    @NotBlank(message = "Profile is required")
    private String profile = "driving"; // driving, walking, cycling
}


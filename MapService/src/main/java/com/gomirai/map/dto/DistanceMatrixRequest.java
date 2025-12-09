package com.gomirai.map.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistanceMatrixRequest {
    
    @NotEmpty(message = "Origins list cannot be empty")
    @Size(max = 25, message = "Maximum 25 origins allowed")
    @Valid
    private List<GeoPoint> origins;
    
    @NotEmpty(message = "Destinations list cannot be empty")
    @Size(max = 25, message = "Maximum 25 destinations allowed")
    @Valid
    private List<GeoPoint> destinations;
    
    @NotNull(message = "Profile is required")
    private String profile = "driving"; // driving, walking, cycling
}




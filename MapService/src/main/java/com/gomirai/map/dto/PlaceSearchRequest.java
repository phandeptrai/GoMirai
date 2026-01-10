package com.gomirai.map.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceSearchRequest {
    
    @NotBlank(message = "Query is required")
    private String query;
    
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 10, message = "Limit must be at most 10")
    private Integer limit = 5;
    
    private String proximity; // Format: "longitude,latitude" (optional)
}







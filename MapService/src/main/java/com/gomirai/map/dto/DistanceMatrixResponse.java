package com.gomirai.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DistanceMatrixResponse {
    private List<List<DistanceMatrixElement>> matrix;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DistanceMatrixElement {
        private Double distance;  // meters
        private Integer duration; // seconds
    }
}


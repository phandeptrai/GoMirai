package com.gomirai.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse {
    private Double distance;           // Khoảng cách (meters)
    private Integer duration;            // Thời gian (seconds)
    private List<GeoPoint> geometry;    // Tọa độ các điểm trên lộ trình
    private String polyline;            // Encoded polyline (cho bản đồ)
    private List<RouteStep> steps;       // Các bước chỉ đường
}




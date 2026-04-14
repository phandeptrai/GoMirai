package com.gomirai.ride.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteResponse implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private Double distance;           // Khoảng cách (meters)
    private Integer duration;            // Thời gian (seconds)
    private List<GeoPoint> geometry;    // Tọa độ các điểm trên lộ trình
    private String polyline;            // Encoded polyline (cho bản đồ)
    private List<RouteStep> steps;       // Các bước chỉ đường
}







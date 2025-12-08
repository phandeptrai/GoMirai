package com.gomirai.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteStep {
    private String instruction;          // Hướng dẫn (ví dụ: "Rẽ trái vào Nguyễn Huệ")
    private Double distance;            // Khoảng cách bước này (meters)
    private Integer duration;            // Thời gian bước này (seconds)
    private GeoPoint location;           // Vị trí bước này
}


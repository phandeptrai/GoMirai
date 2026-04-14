package com.gomirai.ride.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlaceResponse {
    private String id;                 // Place ID
    private String name;               // Tên địa điểm
    private String address;            // Địa chỉ đầy đủ
    private GeoPoint location;         // Tọa độ
    private String category;           // Loại địa điểm (restaurant, hotel, etc.)
    private String type;               // place, poi, address, etc.
}







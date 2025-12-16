package com.gomirai.map.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReverseGeocodeResponse {
    private String address;             // Địa chỉ đầy đủ
    private String street;              // Tên đường
    private String district;            // Quận/Huyện
    private String city;                 // Thành phố
    private String country;              // Quốc gia
}







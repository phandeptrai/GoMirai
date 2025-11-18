package com.gomirai.user.dto;

import java.util.Date;
import java.util.UUID;

import com.gomirai.user.model.Address;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    private UUID userId;
    private String fullName;
    private String phone;
    private String email;
    private Address address;
    private Date dateOfBirth;
    private ProfileStatus status;
    
    public enum ProfileStatus {
        PENDING,     // Profile chưa được tạo (đang chờ Kafka xử lý)
        INCOMPLETE,  // Profile đã tạo nhưng thiếu thông tin
        COMPLETE     // Profile đầy đủ thông tin
    }
}








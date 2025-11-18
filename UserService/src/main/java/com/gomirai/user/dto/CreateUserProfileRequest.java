package com.gomirai.user.dto;

import java.util.Date;

import com.gomirai.user.model.Address;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileRequest {
    private String fullName;
    private String phone;
    private String email;
    private Address address;
    private Date dateOfBirth;
}








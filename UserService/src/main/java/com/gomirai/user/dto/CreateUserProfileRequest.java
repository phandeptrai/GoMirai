package com.gomirai.user.dto;

import java.util.Date;

import com.gomirai.user.model.Address;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileRequest {
    
    @NotBlank(message = "Full name is required")
    private String fullName;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{9,11}$", message = "Phone number must be 9-11 digits")
    private String phone;
    
    @Email(message = "Invalid email format")
    private String email;
    
    private Address address;
    private Date dateOfBirth;
}

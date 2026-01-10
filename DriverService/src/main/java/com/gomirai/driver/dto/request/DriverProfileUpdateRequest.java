package com.gomirai.driver.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DriverProfileUpdateRequest(
	@NotBlank(message = "License number is required")
	@Size(max = 64, message = "License number cannot exceed 64 characters")
	String licenseNumber
) { }


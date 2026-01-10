package com.gomirai.driver.dto.request;

import com.gomirai.common.enums.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record DriverApplicationRequest(
	@NotBlank(message = "License number is required")
	@Size(max = 64, message = "License number cannot exceed 64 characters")
	String licenseNumber,

	@NotBlank(message = "Vehicle brand is required")
	@Size(max = 64, message = "Vehicle brand cannot exceed 64 characters")
	String vehicleBrand,

	@NotBlank(message = "Vehicle model is required")
	@Size(max = 64, message = "Vehicle model cannot exceed 64 characters")
	String vehicleModel,

	@NotBlank(message = "Plate number is required")
	@Size(max = 32, message = "Plate number cannot exceed 32 characters")
	String plateNumber,

	@NotBlank(message = "Vehicle color is required")
	@Size(max = 32, message = "Vehicle color cannot exceed 32 characters")
	String color,

	@NotNull(message = "Vehicle type is required")
	VehicleType vehicleType,

	@NotNull(message = "Registration date is required")
	@PastOrPresent(message = "Registration date cannot be in the future")
	LocalDate registrationDate
) { }


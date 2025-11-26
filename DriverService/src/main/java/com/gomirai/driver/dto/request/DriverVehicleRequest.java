package com.gomirai.driver.dto.request;

import java.time.LocalDate;

import com.gomirai.driver.enums.VehicleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record DriverVehicleRequest(
	@NotBlank(message = "Vehicle brand is required")
	@Size(max = 64, message = "Vehicle brand cannot exceed 64 characters")
	String brand,

	@NotBlank(message = "Vehicle model is required")
	@Size(max = 64, message = "Vehicle model cannot exceed 64 characters")
	String model,

	@NotBlank(message = "Plate number is required")
	@Size(max = 32, message = "Plate number cannot exceed 32 characters")
	String plateNumber,

	@NotBlank(message = "Vehicle color is required")
	@Size(max = 32, message = "Vehicle color cannot exceed 32 characters")
	String color,

	@NotNull(message = "Vehicle type is required")
	VehicleType type,

	@NotNull(message = "Registration date is required")
	@PastOrPresent(message = "Registration date cannot be in the future")
	LocalDate registrationDate
) { }


package com.gomirai.driver.dto.response;

import java.time.LocalDate;
import java.util.UUID;

import com.gomirai.driver.enums.VehicleType;

public record DriverVehicleResponse(
	UUID vehicleId,
	String brand,
	String model,
	String plateNumber,
	String color,
	VehicleType type,
	LocalDate registrationDate
) { }


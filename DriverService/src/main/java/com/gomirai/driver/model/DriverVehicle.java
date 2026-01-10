package com.gomirai.driver.model;

import java.time.LocalDate;
import java.util.UUID;

import com.gomirai.common.enums.VehicleType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverVehicle {
	private UUID vehicleId;
	private String brand;
	private String model;
	private String plateNumber;
	private String color;
	private VehicleType type;
	private LocalDate registrationDate;
}


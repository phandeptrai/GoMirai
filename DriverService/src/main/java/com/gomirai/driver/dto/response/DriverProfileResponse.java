package com.gomirai.driver.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.gomirai.driver.enums.DriverAccountStatus;
import com.gomirai.driver.enums.DriverAvailabilityStatus;

public record DriverProfileResponse(
	UUID driverId,
	UUID userId,
	String licenseNumber,
	DriverAccountStatus accountStatus,
	DriverAvailabilityStatus availabilityStatus,
	Double rating,
	Integer completedTrips,
	DriverVehicleResponse vehicle,
	Instant createdAt,
	Instant updatedAt
) { }


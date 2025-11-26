package com.gomirai.driver.dto.response;

import java.util.UUID;

import com.gomirai.driver.enums.DriverAccountStatus;
import com.gomirai.driver.enums.DriverAvailabilityStatus;

public record DriverStatusResponse(
	UUID driverId,
	DriverAccountStatus accountStatus,
	DriverAvailabilityStatus availabilityStatus
) { }


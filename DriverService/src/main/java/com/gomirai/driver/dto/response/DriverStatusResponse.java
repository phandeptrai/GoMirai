package com.gomirai.driver.dto.response;

import java.util.UUID;

import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.common.enums.DriverAvailabilityStatus;

public record DriverStatusResponse(
	UUID driverId,
	DriverAccountStatus accountStatus,
	DriverAvailabilityStatus availabilityStatus
) { }


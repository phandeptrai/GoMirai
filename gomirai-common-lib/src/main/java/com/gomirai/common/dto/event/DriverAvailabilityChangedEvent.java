package com.gomirai.common.dto.event;

import java.util.UUID;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;

/**
 * Kafka Event: Driver Availability Changed
 *
 * Published by: DriverService
 * Consumed by: TrackingService (và các service khác nếu cần)
 */
public record DriverAvailabilityChangedEvent(
	UUID driverId,
	DriverAvailabilityStatus availabilityStatus,
	VehicleType vehicleType
) {
	public void validate() {
		if (driverId == null) {
			throw new IllegalArgumentException("driverId cannot be null");
		}
		if (availabilityStatus == null) {
			throw new IllegalArgumentException("availabilityStatus cannot be null");
		}
	}
}





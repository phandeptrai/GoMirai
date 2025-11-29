package com.gomirai.driver.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.common.enums.DriverAvailabilityStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_profiles")
public class DriverProfile {

	@Id
	private UUID driverId;

	@Indexed(unique = true)
	private UUID userId;

	private String licenseNumber;
	private DriverAccountStatus accountStatus;
	private DriverAvailabilityStatus availabilityStatus;
	private Double rating;
	private Integer completedTrips;
	private DriverVehicle vehicle;
	private Instant createdAt;
	private Instant updatedAt;
}


package com.gomirai.common.dto.event;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;

public record DriverApprovedEvent(
	@JsonProperty("userId") UUID userId,
	@JsonProperty("driverId") UUID driverId,
	@JsonProperty("timestamp") String timestamp
) {}


package com.gomirai.common.dto.event;

import java.util.UUID;

public record DriverApprovedEvent(
	UUID userId,
	UUID driverId,
	String timestamp
) {}

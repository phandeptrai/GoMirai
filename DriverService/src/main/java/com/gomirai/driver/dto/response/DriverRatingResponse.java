package com.gomirai.driver.dto.response;

import java.util.UUID;

public record DriverRatingResponse(
	UUID driverId,
	Double rating
) { }


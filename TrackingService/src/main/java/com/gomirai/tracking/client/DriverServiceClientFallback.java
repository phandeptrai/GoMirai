package com.gomirai.tracking.client;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Fallback to prevent tracking failure if driver-service is down.
 */
@Component
public class DriverServiceClientFallback implements DriverServiceClient {

    @Override
    public List<Object> getProfilesByDriverIds(List<UUID> driverIds) {
        return List.of();
    }
}

package com.gomirai.tracking.client;

import java.util.List;
import java.util.UUID;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "driver-service", fallback = DriverServiceClientFallback.class)
public interface DriverServiceClient {

    @PostMapping("/api/driver/bulk/by-driver-ids")
    List<Object> getProfilesByDriverIds(@RequestBody List<UUID> driverIds);
}

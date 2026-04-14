package com.gomirai.communication.notification.controller;

import com.gomirai.communication.notification.dto.request.RegisterDeviceRequest;
import com.gomirai.communication.notification.service.DeviceTokenService;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notification/device-tokens")
public class DeviceTokenController {

    private final DeviceTokenService service;

    public DeviceTokenController(DeviceTokenService service) {
        this.service = service;
    }

    @Bulkhead(name = "communicationRestApi")
    @PostMapping
    public void register(@RequestBody RegisterDeviceRequest req) {
        service.register(req);
    }
}

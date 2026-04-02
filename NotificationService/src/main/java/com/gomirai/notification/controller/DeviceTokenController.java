package com.gomirai.notification.controller;

import com.gomirai.notification.dto.request.RegisterDeviceRequest;
import com.gomirai.notification.service.DeviceTokenService;
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

    @PostMapping
    public void register(@RequestBody RegisterDeviceRequest req) {
        service.register(req);
    }
}

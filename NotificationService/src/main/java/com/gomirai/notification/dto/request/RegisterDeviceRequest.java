package com.gomirai.notification.dto.request;

import com.gomirai.notification.enums.DeviceType;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterDeviceRequest {

    private UUID userId;
    private String fcmToken;
    private DeviceType deviceType;
}

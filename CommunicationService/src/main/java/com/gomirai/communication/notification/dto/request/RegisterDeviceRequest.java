package com.gomirai.communication.notification.dto.request;

import com.gomirai.communication.notification.enums.DeviceType;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterDeviceRequest {

    private UUID userId;
    private String fcmToken;
    private DeviceType deviceType;
}

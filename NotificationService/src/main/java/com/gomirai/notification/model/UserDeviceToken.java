package com.gomirai.notification.model;

import com.gomirai.notification.enums.DeviceType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Document(collection = "user_device_tokens")
public class UserDeviceToken {

    @Id
    private String id;

    private UUID userId;

    private String fcmToken;

    private DeviceType deviceType;

    private LocalDateTime lastUpdated;
}

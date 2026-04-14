package com.gomirai.communication.notification.dto.request;

import com.gomirai.communication.notification.enums.NotificationType;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateNotificationRequest {

    private UUID userId;
    private NotificationType type;
    private String title;
    private String message;
}

package com.gomirai.notification.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationResponse {

    private String id;
    private String title;
    private String message;
    private boolean isRead;
    private LocalDateTime createdAt;
}

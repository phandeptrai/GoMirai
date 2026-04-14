package com.gomirai.communication.notification.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.gomirai.common.enums.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    // Strict Unique Index for Idempotency
    @Indexed(unique = true)
    private UUID eventId;

    @Indexed
    private UUID userId;

    private NotificationType type;

    private Map<String, Object> payload;

    private boolean isRead;

    private Instant createdAt;
}

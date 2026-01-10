package com.gomirai.common.dto.event;

import java.util.Map;
import java.util.UUID;

import com.gomirai.common.enums.NotificationType;


import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

@EqualsAndHashCode(callSuper = true)
public class NotificationEvent extends BaseEvent {
    
    private UUID eventId; // Unique ID for idempotency (required)
    private UUID targetUserId; // Target user or driver ID
    private NotificationType type;
    private Map<String, Object> payload;
    private boolean isRealtimeOnly; // True = Fire & Forget (WebSocket only), False = Save to DB

    public NotificationEvent(UUID eventId, UUID targetUserId, NotificationType type, Map<String, Object> payload, boolean isRealtimeOnly) {
        init("NotificationEvent", "BusinessService");
        this.eventId = eventId;
        this.targetUserId = targetUserId;
        this.type = type;
        this.payload = payload;
        this.isRealtimeOnly = isRealtimeOnly;
    }
}

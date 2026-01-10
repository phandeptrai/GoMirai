package com.gomirai.notification.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.NotificationEvent;
import com.gomirai.notification.service.NotificationPersistenceService;
import com.gomirai.notification.service.RealtimePushService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final RealtimePushService realtimePushService;
    private final NotificationPersistenceService persistenceService;

    // Listen ONLY to notification.events
    @KafkaListener(topics = "notification.events", groupId = "notification-service")
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Received Notification Event: [ID={}] [Type={}] [User={}]", 
                 event.getEventId(), event.getType(), event.getTargetUserId());

        if (event.isRealtimeOnly()) {
            // Fire and Forget - No DB
            realtimePushService.push(event);
        } else {
            // Persist (Idempotent) + Push
            persistenceService.processPersistedNotification(event);
        }
    }
}

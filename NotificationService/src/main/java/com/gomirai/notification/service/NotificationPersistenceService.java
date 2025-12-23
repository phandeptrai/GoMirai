package com.gomirai.notification.service;

import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.gomirai.common.dto.event.NotificationEvent;
import com.gomirai.notification.model.Notification;
import com.gomirai.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final RealtimePushService realtimePushService;

    public void processPersistedNotification(NotificationEvent event) {
        try {
            Notification savedNotification = saveNotification(event);
            log.info("Notification persisted successfully: {}", savedNotification.getId());
            // After persisting, push to realtime as well
            realtimePushService.push(event);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate Notification Event detected: {}. Ignoring.", event.getEventId());
        } catch (Exception e) {
            log.error("Error saving notification: {}", e.getMessage());
        }
    }

    private Notification saveNotification(NotificationEvent event) {
        Notification notification = Notification.builder()
            .eventId(event.getEventId())
            .userId(event.getTargetUserId())
            .type(event.getType())
            .payload(event.getPayload())
            .isRead(false)
            .createdAt(Instant.now())
            .build();
            
        return notificationRepository.save(notification);
    }
}

package com.gomirai.communication.notification.service;

import com.gomirai.communication.notification.dto.request.CreateNotificationRequest;
import com.gomirai.communication.notification.model.Notification;
import com.gomirai.communication.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    public Notification create(CreateNotificationRequest req) {
        Notification n = new Notification();
        n.setUserId(req.getUserId());
        // Map local NotificationType to common NotificationType if needed, or use common type everywhere
        // Assuming req.getType() returns com.gomirai.communication.notification.enums.NotificationType and n.setType expects com.gomirai.common.enums.NotificationType
        // We need to convert or unify. For now, let's assume we need to convert by name.
        n.setType(com.gomirai.common.enums.NotificationType.valueOf(req.getType().name()));
        
        // Notification model does not have title/message fields in the provided file, it has payload.
        // We should add title/message to payload or update Notification model.
        // Based on error, Notification model is missing setTitle/setMessage.
        // Let's put them in payload for now as the model has payload map.
        n.setPayload(java.util.Map.of(
            "title", req.getTitle(),
            "message", req.getMessage()
        ));
        
        n.setRead(false);
        n.setCreatedAt(Instant.now());
        return repo.save(n);
    }

    public List<Notification> getByUser(UUID userId) {
        return repo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void markAsRead(String id) {
        Notification n = repo.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        n.setRead(true);
        repo.save(n);
    }
}

package com.gomirai.communication.notification.controller;

import com.gomirai.communication.notification.dto.request.CreateNotificationRequest;
import com.gomirai.communication.notification.dto.response.NotificationResponse;
import com.gomirai.communication.notification.model.Notification;
import com.gomirai.communication.notification.service.NotificationService;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Controller quản lý thông báo (Notification).
 * 
 * Chức năng: Tạo notification, lấy danh sách, đánh dấu đã đọc.
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @Bulkhead(name = "communicationRestApi")
    @PostMapping
    public NotificationResponse create(@RequestBody CreateNotificationRequest req) {
        Notification n = service.create(req);
        String title = (String) n.getPayload().getOrDefault("title", "");
        String message = (String) n.getPayload().getOrDefault("message", "");
        return new NotificationResponse(
                n.getId(),
                title,
                message,
                n.isRead(),
                LocalDateTime.ofInstant(n.getCreatedAt(), ZoneId.systemDefault()));
    }

    @Bulkhead(name = "communicationRestApi")
    @GetMapping("/user/{userId}")
    public List<Notification> getByUser(@PathVariable UUID userId) {
        return service.getByUser(userId);
    }

    @Bulkhead(name = "communicationRestApi")
    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable String id) {
        service.markAsRead(id);
    }
}

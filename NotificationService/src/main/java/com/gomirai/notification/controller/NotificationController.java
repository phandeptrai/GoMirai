package com.gomirai.notification.controller;

import com.gomirai.notification.dto.request.CreateNotificationRequest;
import com.gomirai.notification.dto.response.NotificationResponse;
import com.gomirai.notification.model.Notification;
import com.gomirai.notification.service.NotificationService;
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
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

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

    @GetMapping("/user/{userId}")
    public List<Notification> getByUser(@PathVariable UUID userId) {
        return service.getByUser(userId);
    }

    @PatchMapping("/{id}/read")
    public void markAsRead(@PathVariable String id) {
        service.markAsRead(id);
    }
}

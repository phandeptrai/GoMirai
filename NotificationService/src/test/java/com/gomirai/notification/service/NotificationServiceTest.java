package com.gomirai.notification.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gomirai.notification.dto.request.CreateNotificationRequest;
import com.gomirai.notification.enums.NotificationType;
import com.gomirai.notification.model.Notification;
import com.gomirai.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository repository;

    @InjectMocks
    private NotificationService notificationService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("1. Create Notification - Success")
    void create_Success() {
        // Arrange
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setUserId(userId);
        req.setType(NotificationType.PAYMENT_SUCCESS);
        req.setTitle("Driver Found");
        req.setMessage("Tài xế đang đến");

        when(repository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Notification result = notificationService.create(req);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("Driver Found", result.getPayload().get("title"));
        assertFalse(result.isRead());
    }

    @Test
    @DisplayName("2. Mark As Read - Success")
    void markAsRead_Success() {
        // Arrange
        String id = "notif-1";
        Notification n = new Notification();
        n.setId(id);
        n.setRead(false);

        when(repository.findById(id)).thenReturn(Optional.of(n));
        when(repository.save(any())).thenReturn(n);

        // Act
        notificationService.markAsRead(id);

        // Assert
        assertTrue(n.isRead());
        verify(repository).save(n);
    }
}

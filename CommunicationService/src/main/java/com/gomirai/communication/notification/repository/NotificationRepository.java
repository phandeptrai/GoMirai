package com.gomirai.communication.notification.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.gomirai.communication.notification.model.Notification;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    
    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    boolean existsByEventId(UUID eventId);
}

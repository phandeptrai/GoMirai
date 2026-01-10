package com.gomirai.notification.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import com.gomirai.common.dto.event.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealtimePushService {

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;

    public void push(NotificationEvent event) {
        String userId = event.getTargetUserId().toString();
        
        log.info("Pushing realtime event {} to user {}", event.getType(), userId);
        
        // Debug: Check if user is connected
        SimpUser user = userRegistry.getUser(userId);
        if (user == null) {
            log.warn("⚠️ User {} NOT FOUND in SimpUserRegistry! Cannot deliver message.", userId);
            log.info("Connected users: {}", userRegistry.getUserCount());
            userRegistry.getUsers().forEach(u -> log.info("  - User: {}", u.getName()));
            return;
        }
        
        log.info("✓ User {} found in registry with {} sessions", userId, user.getSessions().size());
        
        // Extract the actual payload based on event type
        // NotificationEvent is ONLY for internal routing - WebSocket sends original payload
        Object payloadToSend;
        
        switch (event.getType().name()) {
            case "DRIVER_OFFER":
                // Driver realtime: Send DriverBookingOfferResponse directly
                payloadToSend = event.getPayload().get("offer");
                log.info("Sending DRIVER_OFFER payload directly (backward compatible)");
                break;
                
            case "BOOKING_UPDATE":
                // Customer realtime: Send BookingStatusResponse directly
                payloadToSend = event.getPayload().get("booking");
                log.info("Sending BOOKING_UPDATE payload directly (backward compatible)");
                break;
                
            default:
                // For other types, send the entire NotificationEvent
                payloadToSend = event;
                log.info("Sending full NotificationEvent for type: {}", event.getType());
                break;
        }
        
        // Send to each session of the user
        user.getSessions().forEach(session -> {
            String sessionId = session.getId();
            String destination = "/queue/realtime";
            
            log.info("Sending to session {} at destination {}", sessionId, destination);
            
            // Send directly to session using convertAndSendToUser with sessionId
            messagingTemplate.convertAndSendToUser(
                sessionId,
                destination,
                payloadToSend
            );
        });
        
        log.info("Successfully pushed to all sessions of user {}", userId);
    }
}

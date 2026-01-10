package com.gomirai.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Pure WebSocket push service - NO business logic, NO database queries
 * Just Kafka event → WebSocket push
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketPushService {
    
    private final SimpMessagingTemplate messagingTemplate;
    
    /**
     * Push driver offer to user via WebSocket
     * Envelope format: { type: "DRIVER_OFFER", payload: {...} }
     */
    public void pushDriverOffer(String userId, Object payload) {
        long pushTs = System.currentTimeMillis();
        Map<String, Object> envelope = Map.of(
            "type", "DRIVER_OFFER",
            "payload", payload
        );
        
        log.info("=== [WS PUSH] DRIVER_OFFER ===");
        log.info("[WS PUSH] timestamp={}, userId={}", pushTs, userId);
        log.info("[WS PUSH] destination=/user/{}/queue/realtime", userId);
        log.info("[WS PUSH] envelope.type={}", envelope.get("type"));
        log.info("[WS PUSH] payload={}", payload);
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/realtime", envelope);
        
        log.info("[WS PUSH] ✓ Completed at {}", pushTs);
    }
    
    /**
     * Push booking status update to user
     * Envelope format: { type: "BOOKING_STATUS", payload: {...} }
     */
    public void pushBookingStatus(String userId, Object payload) {
        Map<String, Object> envelope = Map.of(
            "type", "BOOKING_STATUS",
            "payload", payload
        );
        
        log.info("Pushing BOOKING_STATUS to user {} via /user/queue/realtime", userId);
        messagingTemplate.convertAndSendToUser(userId, "/queue/realtime", envelope);
    }
    
    /**
     * Push general notification to user
     * Envelope format: { type: "NOTIFICATION", payload: {...} }
     */
    public void pushNotification(String userId, Object payload) {
        Map<String, Object> envelope = Map.of(
            "type", "NOTIFICATION",
            "payload", payload
        );
        
        messagingTemplate.convertAndSendToUser(userId, "/queue/realtime", envelope);
    }
}

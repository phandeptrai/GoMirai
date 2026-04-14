package com.gomirai.communication.notification.listener;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.gomirai.communication.notification.service.WebSocketSessionRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketSessionRegistry registry;

    @EventListener
    public void handleConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String userId = (String) headerAccessor.getSessionAttributes().get("userId");
        String sessionId = headerAccessor.getSessionId();

        if (userId != null) {
            log.info("Received new WebSocket connection. UserId: {}, SessionId: {}", userId, sessionId);
            registry.registerSession(sessionId, userId);
        }
    }

    @EventListener
    public void handleDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        registry.removeSession(sessionId);
        log.info("WebSocket connection disconnected. SessionId: {}", sessionId);
    }
}

package com.gomirai.notification.interceptor;

import java.net.URI;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class UserHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        // Extract userId from query param: ws://host/ws?userId=xyz
        String query = uri.getQuery();
        
        log.info("=== [WS HANDSHAKE] ===");
        log.info("[WS HANDSHAKE] URI: {}", uri);
        log.info("[WS HANDSHAKE] Query: {}", query);
        
        if (query != null) {
             // Manual parsing since we are in HandshakeInterceptor context
             // Simple split approach for robustness if UriComponentsBuilder fails context
             String userId = extractUserId(query);
             
            if (userId != null && !userId.isBlank()) {
                attributes.put("userId", userId);
                log.info("[WS HANDSHAKE] ✓ APPROVED for userId: {} (exact string)", userId);
                log.info("[WS HANDSHAKE] userId.length={}", userId.length());
                return true;
            }
        }
        
        log.warn("WebSocket Handshake REJECTED: missing userId in query params. URI: {}", uri);
        return false;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    private String extractUserId(String query) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2 && "userId".equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}

package com.gomirai.notification.interceptor;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.gomirai.common.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket Handshake Interceptor - Xác thực JWT token khi mở kết nối WebSocket
 * 
 * Client kết nối với: ws://host/ws?token=<JWT_TOKEN>
 * Interceptor sẽ:
 * 1. Extract JWT token từ query parameter
 * 2. Validate token using JwtService từ common lib
 * 3. Extract userId từ token và lưu vào session attributes
 * 4. Reject handshake nếu token không hợp lệ
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) throws Exception {

        URI uri = request.getURI();
        String query = uri.getQuery();

        log.info("=== [WS HANDSHAKE] ===");
        log.info("[WS HANDSHAKE] URI: {}", uri);

        if (query == null) {
            log.warn("[WS HANDSHAKE] ✗ REJECTED: No query parameters");
            return false;
        }

        // Extract token từ query param: ws://host/ws?token=<JWT>
        String token = extractQueryParam(query, "token");

        if (token == null || token.isBlank()) {
            log.warn("[WS HANDSHAKE] ✗ REJECTED: Missing token parameter. URI: {}", uri);
            return false;
        }

        // URL decode token (frontend encodes it)
        try {
            token = java.net.URLDecoder.decode(token, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[WS HANDSHAKE] ✗ REJECTED: Failed to decode token");
            return false;
        }

        // Validate JWT token using common lib JwtService
        if (!jwtService.validateToken(token)) {
            log.warn("[WS HANDSHAKE] ✗ REJECTED: Invalid or expired token");
            return false;
        }

        // Extract userId từ token
        Optional<UUID> userIdOpt = jwtService.extractUserId(token);

        if (userIdOpt.isEmpty()) {
            log.warn("[WS HANDSHAKE] ✗ REJECTED: Cannot extract userId from token");
            return false;
        }

        String userId = userIdOpt.get().toString();
        attributes.put("userId", userId);

        // Optional: Extract role để có thể dùng sau này
        jwtService.extractRole(token).ifPresent(role -> {
            attributes.put("userRole", role);
            log.info("[WS HANDSHAKE] User role: {}", role);
        });

        log.info("[WS HANDSHAKE] ✓ APPROVED for userId: {}", userId);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    /**
     * Extract query parameter value by key
     */
    private String extractQueryParam(String query, String key) {
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && key.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }
}

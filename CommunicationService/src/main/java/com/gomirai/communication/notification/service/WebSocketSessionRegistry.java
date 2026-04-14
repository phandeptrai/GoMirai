package com.gomirai.communication.notification.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WebSocketSessionRegistry {

    // Map<UserId, ConnectionStatus>
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    public void register(String userId) {
        userSessionMap.put(userId, "CONNECTED");
        log.debug("User registered: {}", userId);
    }

    public void unregister(String userId) {
        // In real app maybe wait a bit before marking offline due to refresh
        userSessionMap.remove(userId);
        log.debug("User unregistered: {}", userId);
    }
    
    // Sometimes session ID is needed for disconnect event mapping
    private final Map<String, String> sessionIdToUserIdMap = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, String userId) {
        sessionIdToUserIdMap.put(sessionId, userId);
        register(userId);
    }

    public String resolveUserId(String sessionId) {
        return sessionIdToUserIdMap.get(sessionId);
    }
    
    public void removeSession(String sessionId) {
        String userId = sessionIdToUserIdMap.remove(sessionId);
        if (userId != null) {
            unregister(userId);
        }
    }

    public boolean isOnline(String userId) {
        return userSessionMap.containsKey(userId);
    }
}

package com.gomirai.communication.notification.config;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            ServerHttpRequest request,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        
        // Get userId from attributes (set by UserHandshakeInterceptor)
        String userId = (String) attributes.get("userId");
        
        if (userId != null) {
            log.info("Determined user Principal: {}", userId);
            return new StompPrincipal(userId);
        }
        
        log.warn("No userId found in attributes, returning null Principal");
        return null;
    }
    
    /**
     * Simple Principal implementation for STOMP
     */
    private static class StompPrincipal implements Principal {
        private final String name;
        
        public StompPrincipal(String name) {
            this.name = name;
        }
        
        @Override
        public String getName() {
            return name;
        }
    }
}

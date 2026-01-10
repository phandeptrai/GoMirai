package com.gomirai.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.gomirai.notification.interceptor.UserHandshakeInterceptor;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final UserHandshakeInterceptor userHandshakeInterceptor;
    private final CustomHandshakeHandler customHandshakeHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Main WebSocket endpoint
        registry.addEndpoint("/ws")
                .addInterceptors(userHandshakeInterceptor)
                .setHandshakeHandler(customHandshakeHandler)
                .setAllowedOriginPatterns("*");
        // No SockJS fallback as per requirement
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        
        // Use simple in-memory broker for /topic and /queue
        // NOTE: Do NOT include /user here - it's used as userDestinationPrefix
        registry.enableSimpleBroker("/topic", "/queue");
        
        // User destination prefix for convertAndSendToUser
        registry.setUserDestinationPrefix("/user");
    }
}

package com.gomirai.user.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka consumer for UserRegisteredEvent.
 * 
 * Handles user registration events from both:
 * 1. LOCAL auth: user registered with phone + password
 * 2. OAuth auth: user registered via Google/Facebook/etc.
 * 
 * For LOCAL auth: phoneNumber is present, email may be null
 * For OAuth auth: email is present, phoneNumber may be null
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventConsumer {

    private final UserProfileService userProfileService;

    @KafkaListener(topics = "${kafka.topic.user-registered:user-registered}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, phoneNumber={}, role={}, email={}, authProvider={}",
                event.userId(), event.phoneNumber(), event.role(), event.email(), event.authProvider());

        try {
            // Determine if this is LOCAL or OAuth registration
            String authProvider = event.authProvider();
            boolean isOAuthUser = authProvider != null && !"LOCAL".equalsIgnoreCase(authProvider);

            if (isOAuthUser) {
                // OAuth user: create profile with email and fullName
                log.info("Creating profile for OAuth ({}) user: {} with email: {}",
                        authProvider, event.userId(), event.email());
                userProfileService.createOAuthUserProfile(
                        event.userId(),
                        event.email(),
                        event.fullName(),
                        authProvider);
                log.info("Successfully created OAuth user profile for userId: {}", event.userId());
            } else {
                // LOCAL user: create profile with phone number
                log.info("Creating profile for LOCAL user: {} with phone: {}",
                        event.userId(), event.phoneNumber());
                userProfileService.createEmptyUserProfile(event.userId(), event.phoneNumber());
                log.info("Successfully created LOCAL user profile for userId: {} with phone: {}",
                        event.userId(), event.phoneNumber());
            }
        } catch (Exception e) {
            log.error("Failed to create user profile for userId: {}", event.userId(), e);
            // Có thể thêm retry logic hoặc dead letter queue ở đây
        }
    }
}

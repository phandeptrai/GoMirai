package com.gomirai.user.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.UserRegisteredEvent;
import com.gomirai.user.service.UserProfileService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredEventConsumer {

    private final UserProfileService userProfileService;

    @KafkaListener(
        topics = "${kafka.topic.user-registered:user-registered}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, phoneNumber={}, role={}", 
            event.userId(), event.phoneNumber(), event.role());
        
        try {
            // Tạo UserProfile với số điện thoại từ event đăng ký, các giá trị khác null
            userProfileService.createEmptyUserProfile(event.userId(), event.phoneNumber());
            log.info("Successfully created empty user profile for userId: {} with phone: {}", 
                event.userId(), event.phoneNumber());
        } catch (Exception e) {
            log.error("Failed to create user profile for userId: {}", event.userId(), e);
            // Có thể thêm retry logic hoặc dead letter queue ở đây
        }
    }
}



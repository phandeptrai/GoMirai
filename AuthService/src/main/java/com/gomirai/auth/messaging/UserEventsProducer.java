package com.gomirai.auth.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.UserRegisteredEvent;

/**
 * Kafka Producer gửi sự kiện đăng ký user.
 * 
 * Khi user đăng ký thành công (LOCAL hoặc Google OAuth),
 * producer này gửi event UserRegistered để các service khác xử lý:
 * 
 * - UserService: Tạo UserProfile với thông tin user
 * - PaymentService: Tạo Wallet cho user mới
 * 
 * Kiến trúc Event-Driven:
 * - AuthService chỉ xử lý authentication
 * - Các service khác tự lắng nghe và xử lý phần của mình
 * - Loosely coupled, dễ mở rộng
 */
@Component
public class UserEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userRegisteredTopic;

    public UserEventsProducer(KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topic.user-registered:user-registered}") String userRegisteredTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.userRegisteredTopic = userRegisteredTopic;
    }

    /**
     * Publish sự kiện user đăng ký thành công
     * 
     * Consumers:
     * 1. UserService (UserRegisteredEventConsumer) - Tạo UserProfile
     * 2. PaymentService (UserKafkaConsumer) - Tạo Wallet cho user mới
     * 
     * Trigger: Sau khi đăng ký LOCAL hoặc Google OAuth thành công
     */
    public void sendUserRegistered(UserRegisteredEvent event) {
        // Sử dụng userId làm key để đảm bảo các message cùng user đi vào cùng partition
        // Điều này giúp duy trì thứ tự xử lý cho cùng một user
        kafkaTemplate.send(userRegisteredTopic, event.userId().toString(), event);
    }
}

package com.gomirai.auth.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.auth.events.UserRegisteredEvent;

@Component
public class UserEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String userRegisteredTopic;

    public UserEventsProducer(KafkaTemplate<String, Object> kafkaTemplate,
                              @Value("${kafka.topic.user-registered:user-registered}") String userRegisteredTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.userRegisteredTopic = userRegisteredTopic;
    }

    public void sendUserRegistered(UserRegisteredEvent event) {
        kafkaTemplate.send(userRegisteredTopic, event.userId().toString(), event);
    }
}




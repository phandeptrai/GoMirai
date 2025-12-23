package com.gomirai.driver.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.NotificationEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String TOPIC = "notification.events";

    public void send(NotificationEvent event) {
        log.info("Publishing NotificationEvent: [ID={}] [Type={}]", event.getEventId(), event.getType());
        kafkaTemplate.send(TOPIC, event.getEventId().toString(), event);
    }
}

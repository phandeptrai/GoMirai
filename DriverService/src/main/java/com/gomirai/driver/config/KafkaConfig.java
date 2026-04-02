package com.gomirai.driver.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Sleep after empty poll batches. Custom {@code kafkaListenerContainerFactory} bypasses Boot's
     * auto wiring of {@code spring.kafka.listener.idle-between-polls}, so we apply it here — reduces
     * idle CPU from 4 Kafka listener threads tight-looping.
     */
    @Value("${spring.kafka.listener.idle-between-polls:500ms}")
    private Duration kafkaListenerIdleBetweenPolls;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, 
            "com.gomirai.driver.event,com.gomirai.booking.event,com.gomirai.common.dto.event,com.gomirai.tracking.event");
        // Mirror producer JsonSerializer.TYPE_MAPPINGS so consumer can resolve aliases
        // from the Kafka __TypeId__ header (e.g. "driverRatingUpdated" -> DriverRatingUpdatedEvent).
        props.put(JsonDeserializer.TYPE_MAPPINGS,
            "driverRatingUpdated:com.gomirai.common.dto.event.DriverRatingUpdatedEvent");
        props.put(JsonDeserializer.REMOVE_TYPE_INFO_HEADERS, true);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setIdleBetweenPolls(kafkaListenerIdleBetweenPolls.toMillis());
        return factory;
    }
}




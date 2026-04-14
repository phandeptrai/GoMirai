package com.gomirai.communication.review.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration cho ReviewService.
 * 
 * Dùng để publish event DriverRatingUpdatedEvent đến DriverService.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Thêm type mappings cho serialization
        configProps.put(JsonSerializer.TYPE_MAPPINGS,
                "driverRatingUpdated:com.gomirai.common.dto.event.DriverRatingUpdatedEvent");

        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 2_000);
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

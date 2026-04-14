package com.gomirai.ride.booking.model.outbox;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Simplified OutboxEvent for Debezium CDC.
 * Debezium handles relaying, so we don't need status/retry fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Indexed
    private String aggregateId;

    private String aggregateType;

    private String eventType;

    /**
     * Payload as JSON string for Debezium Outbox SMT
     */
    private String payload; 

    /**
     * The target Kafka topic. Debezium will route to this.
     */
    private String topic;

    @Indexed
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}

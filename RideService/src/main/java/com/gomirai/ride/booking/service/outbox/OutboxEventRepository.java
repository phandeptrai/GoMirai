package com.gomirai.ride.booking.service.outbox;

import com.gomirai.ride.booking.model.outbox.OutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OutboxEventRepository extends MongoRepository<OutboxEvent, UUID> {
}

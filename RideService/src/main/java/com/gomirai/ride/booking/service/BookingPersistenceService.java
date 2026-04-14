package com.gomirai.ride.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.ride.booking.model.Booking;
import com.gomirai.ride.booking.repository.BookingRepository;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.ride.booking.messaging.BookingEventsProducer;
import com.gomirai.ride.booking.model.outbox.OutboxEvent;
import com.gomirai.ride.booking.service.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Narrows Mongo transaction scope to persistence only. Publishes driver-search
 * Kafka event
 * after successful commit (or immediately if no transaction is active — e.g.
 * some tests).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingPersistenceService {

    private final BookingRepository bookingRepository;
    private final OutboxEventRepository outboxRepository;
    private final BookingEventsProducer eventsProducer;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.booking-created:booking-created-event}")
    private String createdTopic;

    /**
     * ATOMIC: Save booking and Outbox event in same transaction.
     * Guaranteed that either both exist or neither exists.
     */
    public Booking saveBookingWithOutbox(Booking booking, com.gomirai.common.dto.event.BookingCreatedEvent createdEvent) {
        try {
            // PERF FIX: Move CPU-bound JSON serialization OUTSIDE the database transaction
            // 1. Prepare event with details it can know now
            String jsonPayload = objectMapper.writeValueAsString(createdEvent);

            // 2. Call transactional persistence for DB atoms
            return persistInTransaction(booking, jsonPayload, createdTopic);

        } catch (Exception e) {
            log.error("Failed to prepare booking for outbox", e);
            throw new RuntimeException("Persistence preparation failure: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected Booking persistInTransaction(Booking booking, String jsonPayload, String topic) {
        // 1. Save Booking (Insert first to get the ID)
        Booking saved = bookingRepository.insert(booking);

        // 2. Wrap into Outbox Event (Simple DTO for Debezium) using the NOW known ID
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(saved.getBookingId().toString())
                .aggregateType("BOOKING")
                .eventType("BOOKING_CREATED")
                .payload(jsonPayload)
                .topic(topic)
                .build();

        // 3. Save Outbox (Atomic inside MongoDB transaction)
        outboxRepository.save(outboxEvent);

        log.info("DEBEZIUM OUTBOX INSERTED: bookingId={} (Outbox id: {})",
                saved.getBookingId(), outboxEvent.getId());
        return saved;
    }

    /**
     * Save booking only (Initial step of Async Saga)
     */
    @Transactional
    public Booking saveBookingOnly(Booking booking) {
        return bookingRepository.insert(booking);
    }

    @Transactional
    public Booking saveBookingAndScheduleDriverSearch(Booking booking, BookingSearchDriversEvent searchEvent) {
        Booking saved = bookingRepository.save(booking);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSearchSafely(searchEvent, saved.getBookingId());
                }
            });
        } else {
            publishSearchSafely(searchEvent, saved.getBookingId());
        }

        return saved;
    }

    private void publishSearchSafely(BookingSearchDriversEvent searchEvent, java.util.UUID bookingId) {
        try {
            eventsProducer.publishSearchDriversEvent(searchEvent);
            log.debug("Published driver search event after commit for bookingId={}", bookingId);
        } catch (Exception e) {
            log.error("Failed to publish driver search event for bookingId={}", bookingId, e);
        }
    }
}

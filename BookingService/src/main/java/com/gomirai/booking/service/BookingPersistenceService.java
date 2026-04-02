package com.gomirai.booking.service;

import com.gomirai.booking.messaging.BookingEventsProducer;
import com.gomirai.booking.model.Booking;
import com.gomirai.booking.repository.BookingRepository;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    private final BookingEventsProducer eventsProducer;

    /**
     * Save booking only (Initial step of Async Saga)
     */
    @Transactional
    public Booking saveBookingOnly(Booking booking) {
        return bookingRepository.save(booking);
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

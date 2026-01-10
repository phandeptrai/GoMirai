package com.gomirai.booking.service;

import com.gomirai.booking.model.Booking;
import com.gomirai.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupService {

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;

    @Value("${booking.expiry-timeout-minutes:10}")
    private int expiryTimeoutMinutes;

    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void cleanupExpiredBookings() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(expiryTimeoutMinutes);
        
        List<Booking> expiredBookings = bookingRepository.findExpiredPendingBookings(expiryTime);
        
        if (!expiredBookings.isEmpty()) {
            log.info("Found {} expired PENDING bookings (older than {} minutes)", 
                expiredBookings.size(), expiryTimeoutMinutes);
                
            for (Booking booking : expiredBookings) {
                try {
                    bookingService.cancelBookingNoDriverFound(
                        booking.getBookingId(), 
                        "No driver found within " + expiryTimeoutMinutes + " minutes"
                    );
                } catch (Exception e) {
                    log.error("Failed to auto-cancel expired booking {}", booking.getBookingId(), e);
                }
            }
        }
    }
}

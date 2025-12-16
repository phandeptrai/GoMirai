package com.gomirai.driver.scheduler;

import com.gomirai.driver.repository.DriverBookingOfferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Scheduler để cleanup expired booking offers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingOfferCleanupScheduler {
    
    private final DriverBookingOfferRepository offerRepository;
    
    /**
     * Cleanup expired offers every minute
     */
    @Scheduled(fixedDelayString = "${booking.offer.cleanup-interval-ms:60000}", initialDelay = 60000)
    public void cleanupExpiredOffers() {
        try {
            LocalDateTime now = LocalDateTime.now();
            offerRepository.deleteByExpiresAtBefore(now);
            log.debug("Cleaned up expired booking offers");
        } catch (Exception e) {
            log.error("Error cleaning up expired booking offers", e);
        }
    }
}






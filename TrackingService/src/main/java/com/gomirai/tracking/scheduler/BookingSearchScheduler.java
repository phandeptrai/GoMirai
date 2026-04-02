package com.gomirai.tracking.scheduler;

import com.gomirai.tracking.model.BookingSearchState;
import com.gomirai.tracking.service.BookingCancellationService;
import com.gomirai.tracking.service.BookingSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler để:
 * 1. Tăng bán kính tìm kiếm cho các booking đang chờ tài xế
 * 2. Hủy booking nếu không có tài xế nhận trong 15 phút
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingSearchScheduler {
    
    private final BookingSearchService bookingSearchService;
    private final BookingCancellationService bookingCancellationService;
    
    @Value("${booking.search.expand-interval-minutes:5}")
    private int expandIntervalMinutes;
    
    @Value("${booking.search.max-duration-minutes:15}")
    private int maxSearchDurationMinutes;
    
    /**
     * Chạy mỗi 1 phút để kiểm tra và tăng bán kính tìm kiếm.
     * FIX: Dùng SMEMBERS trên Redis Set thay vì KEYS * để tránh block Redis.
     */
    @Scheduled(fixedDelayString = "${booking.search.check-interval-ms:60000}", initialDelay = 60000)
    public void expandSearchRadiusForPendingBookings() {
        try {
            // FIX: getActiveBookingIds() dùng SMEMBERS thay vì KEYS *
            Set<String> activeIds = bookingSearchService.getActiveBookingIds();
            if (activeIds.isEmpty()) {
                return;
            }
            
            for (String bookingIdStr : activeIds) {
                try {
                    UUID bookingId = UUID.fromString(bookingIdStr);
                    checkAndExpandRadius(bookingId);
                } catch (Exception e) {
                    log.warn("Error processing booking search for id: {}", bookingIdStr, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in booking search scheduler", e);
        }
    }
    
    /**
     * Chạy mỗi 1 phút để hủy các booking không có tài xế nhận trong thời gian tối đa.
     * FIX: Dùng SMEMBERS trên Redis Set thay vì KEYS * để tránh block Redis.
     */
    @Scheduled(fixedDelayString = "${booking.search.check-interval-ms:60000}", initialDelay = 120000)
    public void cancelExpiredBookings() {
        try {
            // FIX: getActiveBookingIds() dùng SMEMBERS thay vì KEYS *
            Set<String> activeIds = bookingSearchService.getActiveBookingIds();
            if (activeIds.isEmpty()) {
                return;
            }
            
            for (String bookingIdStr : activeIds) {
                try {
                    UUID bookingId = UUID.fromString(bookingIdStr);
                    
                    if (shouldCancelBooking(bookingId)) {
                        log.info("Cancelling booking {} - no driver found within {} minutes", 
                            bookingId, maxSearchDurationMinutes);
                        bookingCancellationService.cancelBookingNoDriverFound(bookingId);
                        bookingSearchService.removeSearchState(bookingId);
                    }
                } catch (Exception e) {
                    log.warn("Error processing booking cancellation for id: {}", bookingIdStr, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in booking cancellation scheduler", e);
        }
    }
    
    /**
     * Kiểm tra và tăng bán kính cho một booking cụ thể.
     * FIX: Bỏ isBookingStillPending() HTTP call.
     * Lý do:
     *  - BookingAssignedConsumer và BookingCanceledConsumer đã tự remove search state qua Kafka.
     *  - Nếu search state còn tồn tại → booking chưa được assign/cancel → safe to expand.
     *  - BookingService có guard status check nên duplicate offers không gây double-assign.
     */
    private void checkAndExpandRadius(UUID bookingId) {
        BookingSearchState state = bookingSearchService.getSearchState(bookingId);
        if (state == null || !state.getIsActive()) {
            // State gone → already assigned or canceled, clean up active set
            bookingSearchService.removeSearchState(bookingId);
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration timeSinceLastSearch = Duration.between(state.getLastSearchTime(), now);
        
        if (timeSinceLastSearch.toMinutes() >= expandIntervalMinutes) {
            log.info("Expanding search radius for booking {}", bookingId);
            bookingSearchService.expandSearchRadius(bookingId);
        }
    }
    
    /**
     * Kiểm tra và hủy booking nếu đã quá 15 phút
     */
    private boolean shouldCancelBooking(UUID bookingId) {
        BookingSearchState state = bookingSearchService.getSearchState(bookingId);
        if (state == null || !state.getIsActive()) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration timeSinceStart = Duration.between(state.getSearchStartTime(), now);
        
        // Nếu đã quá maxSearchDurationMinutes và vẫn chưa có tài xế nhận
        return timeSinceStart.toMinutes() >= maxSearchDurationMinutes;
    }
}







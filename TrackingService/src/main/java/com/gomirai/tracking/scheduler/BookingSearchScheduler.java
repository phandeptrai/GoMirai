package com.gomirai.tracking.scheduler;

import com.gomirai.tracking.client.BookingServiceClient;
import com.gomirai.tracking.model.BookingSearchState;
import com.gomirai.tracking.service.BookingCancellationService;
import com.gomirai.tracking.service.BookingSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
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
    private final RedisTemplate<String, String> redisTemplate;
    private final BookingServiceClient bookingServiceClient;
    
    private static final String SEARCH_STATE_KEY_PREFIX = "booking:search:";
    
    @Value("${booking.search.expand-interval-minutes:5}")
    private int expandIntervalMinutes;
    
    @Value("${booking.search.max-duration-minutes:15}")
    private int maxSearchDurationMinutes;
    
    /**
     * Chạy mỗi 1 phút để kiểm tra và tăng bán kính tìm kiếm
     */
    @Scheduled(fixedDelayString = "${booking.search.check-interval-ms:60000}", initialDelay = 60000)
    public void expandSearchRadiusForPendingBookings() {
        try {
            // Lấy tất cả các keys của booking search state
            Set<String> keys = redisTemplate.keys(SEARCH_STATE_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            
            for (String key : keys) {
                try {
                    String bookingIdStr = key.substring(SEARCH_STATE_KEY_PREFIX.length());
                    UUID bookingId = UUID.fromString(bookingIdStr);
                    checkAndExpandRadius(bookingId);
                } catch (Exception e) {
                    log.warn("Error processing booking search key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in booking search scheduler", e);
        }
    }
    
    /**
     * Chạy mỗi 1 phút để hủy các booking không có tài xế nhận trong 15 phút
     */
    @Scheduled(fixedDelayString = "${booking.search.check-interval-ms:60000}", initialDelay = 120000)
    public void cancelExpiredBookings() {
        try {
            // Lấy tất cả các keys của booking search state
            Set<String> keys = redisTemplate.keys(SEARCH_STATE_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return;
            }
            
            for (String key : keys) {
                try {
                    String bookingIdStr = key.substring(SEARCH_STATE_KEY_PREFIX.length());
                    UUID bookingId = UUID.fromString(bookingIdStr);
                    
                    if (shouldCancelBooking(bookingId)) {
                        log.info("Cancelling booking {} - no driver found within {} minutes", 
                            bookingId, maxSearchDurationMinutes);
                        bookingCancellationService.cancelBookingNoDriverFound(bookingId);
                        bookingSearchService.removeSearchState(bookingId);
                    }
                } catch (Exception e) {
                    log.warn("Error processing booking cancellation for key: {}", key, e);
                }
            }
        } catch (Exception e) {
            log.error("Error in booking cancellation scheduler", e);
        }
    }
    
    /**
     * Kiểm tra và tăng bán kính cho một booking cụ thể
     * ONLY expand nếu booking vẫn ở PENDING status
     */
    private void checkAndExpandRadius(UUID bookingId) {
        BookingSearchState state = bookingSearchService.getSearchState(bookingId);
        if (state == null || !state.getIsActive()) {
            return;
        }
        
        // ✅ CHECK: Booking phải vẫn ở PENDING status
        boolean isPending = bookingServiceClient.isBookingStillPending(bookingId);
        if (!isPending) {
            log.info("Booking {} is no longer PENDING, stopping search radius expansion", bookingId);
            bookingSearchService.removeSearchState(bookingId);
            return;
        }
        
        LocalDateTime now = LocalDateTime.now();
        Duration timeSinceLastSearch = Duration.between(state.getLastSearchTime(), now);
        
        // Nếu đã qua expandIntervalMinutes kể từ lần tìm kiếm cuối và vẫn PENDING
        if (timeSinceLastSearch.toMinutes() >= expandIntervalMinutes) {
            log.info("Expanding search radius for booking {} (still PENDING)", bookingId);
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







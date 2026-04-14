package com.gomirai.ride.booking.service;

import com.gomirai.ride.booking.model.Booking;
import com.gomirai.ride.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    /**
     * DISABLED: TrackingService.BookingSearchScheduler đã xử lý timeout booking qua:
     *  1. cancelExpiredBookings() scheduler (chạy mỗi 60s, dùng Redis search state TTL)
     *  2. BookingCanceledConsumer nhận Kafka event và remove search state
     *
     * Bật lại @Scheduled nếu muốn BookingService tự cleanup độc lập với TrackingService.
     * Lưu ý: BookingService.cancelBookingNoDriverFound() đã có status guard nên không
     * gây double-event, nhưng vẫn gây thêm DB reads và potential race condition.
     *
     * Nếu cần bật lại, đảm bảo expiryTimeoutMinutes >= maxSearchDurationMinutes (TrackingService)
     * để BookingService không cancel sớm hơn khi TrackingService vẫn đang tìm tài xế.
     */
    // @Scheduled(fixedRate = 60000)
    // @Transactional
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

package com.gomirai.driver.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "driver_booking_offers")
public class DriverBookingOffer {
    
    @Id
    private String id;
    
    @Indexed
    private UUID bookingId;
    
    @Indexed
    private UUID driverId;
    
    private Double pickupLatitude;
    private Double pickupLongitude;
    private Double dropoffLatitude;
    private Double dropoffLongitude;
    private String vehicleType;
    private Double estimatedDistanceKm;
    private Integer estimatedDurationMinutes;
    private Double estimatedFare;
    private String currency;
    private String pickupAddress;
    private String dropoffAddress;
    
    private LocalDateTime offeredAt;

    /**
     * MongoDB TTL Index: xóa document tự động sau 60s kể từ expiredAt.
     * Điều này ngăn offers cũ tích luỹ và bị load vào JVM heap.
     * Ngoài ra vẫn có BookingOfferCleanupScheduler làm safety net.
     */
    @Indexed(expireAfterSeconds = 60)
    private LocalDateTime expiresAt; // Offer expires after 30 seconds + 30s TTL buffer
    private Boolean isActive; // True if offer is still valid
    
    public DriverBookingOffer(UUID bookingId, UUID driverId) {
        this.bookingId = bookingId;
        this.driverId = driverId;
        this.offeredAt = LocalDateTime.now();
        this.expiresAt = this.offeredAt.plusSeconds(30); // 30 seconds to accept
        this.isActive = true;
    }
}







package com.gomirai.booking.repository;

import com.gomirai.booking.enums.BookingStatus;
import com.gomirai.booking.model.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends MongoRepository<Booking, UUID> {
    
    // Find by customer
    Page<Booking> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
    
    // Find by driver
    Page<Booking> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);
    
    // Find by status
    Page<Booking> findByStatusOrderByCreatedAtDesc(BookingStatus status, Pageable pageable);
    
    // Find by customer and status
    Page<Booking> findByCustomerIdAndStatusOrderByCreatedAtDesc(
        UUID customerId, BookingStatus status, Pageable pageable);
    
    // Find by driver and status
    Page<Booking> findByDriverIdAndStatusOrderByCreatedAtDesc(
        UUID driverId, BookingStatus status, Pageable pageable);
    
    // Find by idempotency key
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);
    
    // Find pending bookings that expired (no driver found)
    @Query("{ 'status': 'PENDING', 'createdAt': { $lt: ?0 } }")
    List<Booking> findExpiredPendingBookings(LocalDateTime expiryTime);
    
    // Find bookings by payment ID
    Optional<Booking> findByPaymentId(UUID paymentId);
}



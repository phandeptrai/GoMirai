package com.gomirai.driver.repository;

import com.gomirai.driver.model.DriverBookingOffer;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverBookingOfferRepository extends MongoRepository<DriverBookingOffer, String> {
    
    List<DriverBookingOffer> findByDriverIdAndIsActiveTrueOrderByOfferedAtDesc(UUID driverId);
    
    Optional<DriverBookingOffer> findByBookingIdAndDriverIdAndIsActiveTrue(UUID bookingId, UUID driverId);
    
    void deleteByDriverIdAndIsActiveFalse(UUID driverId);
    
    void deleteByExpiresAtBefore(LocalDateTime now);
    
    void deleteByBookingId(UUID bookingId); // Delete all offers for a booking when accepted
}







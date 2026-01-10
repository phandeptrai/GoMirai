package com.gomirai.driver.service;

import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.driver.dto.response.DriverBookingOfferResponse;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.common.dto.event.DriverAcceptedEvent;
import com.gomirai.common.dto.event.DriverBookingOfferEvent;
import com.gomirai.driver.messaging.DriverBookingEventsProducer;
import com.gomirai.driver.model.DriverBookingOffer;
import com.gomirai.driver.model.DriverProfile;
import com.gomirai.driver.repository.DriverBookingOfferRepository;
import com.gomirai.driver.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriverBookingService {
    
    private final DriverProfileRepository driverProfileRepository;
    private final DriverBookingEventsProducer eventsProducer;
    private final RestTemplate restTemplate;
    private final DriverBookingOfferRepository offerRepository;
    // WebSocket removed - now handled by NotificationService
    
    @Value("${booking.notification.max-drivers:10}")
    private int maxDriversToNotify;
    
    /**
     * Handle booking search drivers event
     * Finds nearby drivers and sends notifications to all eligible drivers
     */
    public void handleBookingSearchDrivers(BookingSearchDriversEvent event) {
        log.info("Processing BookingSearchDriversEvent: bookingId={}, vehicleType={}, radius={}m", 
            event.getBookingId(), event.getVehicleType(), event.getRadiusMeters());
        
        try {
            // 1. Find nearby drivers using TrackingService
            List<UUID> nearbyDriverIds = findNearbyDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                event.getRadiusMeters(),
                event.getVehicleType()
            );
            
            if (nearbyDriverIds.isEmpty()) {
                log.warn("No nearby drivers found for bookingId={}", event.getBookingId());
                return;
            }
            
            // 2. Filter drivers by availability status (only ONLINE drivers)
            List<UUID> availableDriverIds = filterAvailableDrivers(nearbyDriverIds);
            
            if (availableDriverIds.isEmpty()) {
                log.warn("No available drivers found for bookingId={}", event.getBookingId());
                return;
            }
            
            // 3. Limit number of drivers to notify (to avoid spam)
            List<UUID> driversToNotify = availableDriverIds.stream()
                .limit(maxDriversToNotify)
                .collect(Collectors.toList());
            
            // 4. Send notifications to all eligible drivers
            // In a real implementation, this would send push notifications or WebSocket messages
            // For now, we'll just log it - the actual notification will be handled by frontend polling/WebSocket
            log.info("Notifying {} drivers about bookingId={}: {}", 
                driversToNotify.size(), event.getBookingId(), driversToNotify);
            
            // TODO: Implement actual notification mechanism (WebSocket, Push Notification, etc.)
            // For now, drivers will receive booking info via polling or WebSocket connection
            
        } catch (Exception e) {
            log.error("Error processing BookingSearchDriversEvent for bookingId={}", 
                event.getBookingId(), e);
        }
    }
    
    /**
     * Driver accepts a booking
     * Publishes DriverAcceptedEvent to Kafka
     */
    public void acceptBooking(UUID bookingId, UUID driverId) {
        log.info("Driver {} accepting booking {}", driverId, bookingId);
        
        // Validate driver is available
        DriverProfile driver = driverProfileRepository.findById(driverId)
            .orElseThrow(() -> new RuntimeException("Driver not found: " + driverId));
        
        if (driver.getAvailabilityStatus() != DriverAvailabilityStatus.ONLINE) {
            throw new RuntimeException("Driver is not online");
        }
        
        // Deactivate offer for this driver
        deactivateOffer(bookingId, driverId);
        
        // Delete all offers for this booking (other drivers can't accept anymore)
        deleteOffersForBooking(bookingId);
        
        // Publish event - BookingService will handle atomic update
        DriverAcceptedEvent event = new DriverAcceptedEvent(bookingId, driverId, null);
        eventsProducer.publishDriverAccepted(event);
        
        log.info("Published DriverAcceptedEvent for bookingId={}, driverId={}", bookingId, driverId);
    }
    
    /**
     * Find nearby drivers using TrackingService
     */
    private List<UUID> findNearbyDrivers(Double latitude, Double longitude, Double radiusMeters, String vehicleType) {
        try {
            // Use LoadBalanced RestTemplate - service discovery will resolve TrackingService
            String url = "http://TrackingService/api/tracking/nearby";
            
            // Prepare request
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("latitude", latitude);
            requestBody.put("longitude", longitude);
            requestBody.put("radius", radiusMeters);
            requestBody.put("vehicleType", vehicleType);
            requestBody.put("limit", maxDriversToNotify);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // Call TrackingService
            // Use ParameterizedTypeReference to properly deserialize List<Map<String, Object>>
            ParameterizedTypeReference<List<Map<String, Object>>> responseType = 
                new ParameterizedTypeReference<List<Map<String, Object>>>() {};
            
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                url, HttpMethod.POST, request, responseType);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Extract driver IDs from response
                // Response format: List<DriverLocationResponse> with driverId field
                return response.getBody().stream()
                    .map(driverMap -> {
                        Object driverId = driverMap.get("driverId");
                        if (driverId instanceof String) {
                            return UUID.fromString((String) driverId);
                        } else if (driverId instanceof UUID) {
                            return (UUID) driverId;
                        }
                        return null;
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            }
            
            return List.of();
        } catch (Exception e) {
            log.error("Error finding nearby drivers", e);
            return List.of();
        }
    }
    
    /**
     * Filter drivers by availability status (only ONLINE drivers)
     */
    private List<UUID> filterAvailableDrivers(List<UUID> driverIds) {
        return driverIds.stream()
            .map(driverId -> driverProfileRepository.findById(driverId))
            .filter(optional -> optional.isPresent())
            .map(optional -> optional.get())
            .filter(driver -> driver.getAvailabilityStatus() == DriverAvailabilityStatus.ONLINE)
            .filter(driver -> driver.getAccountStatus().toString().equals("ACTIVE"))
            .map(DriverProfile::getDriverId)
            .collect(Collectors.toList());
    }
    
    /**
     * Handle driver booking offer event from TrackingService
     * This event is sent when a booking is available for the driver to accept
     * PUSH WEBSOCKET DIRECTLY - HARD REALTIME
     */
    public void handleDriverBookingOffer(DriverBookingOfferEvent event) {
        log.info("=== Received DriverBookingOfferEvent ===");
        log.info("bookingId={}, driverId={}, vehicleType={}", 
            event.getBookingId(), event.getDriverId(), event.getVehicleType());
        log.info("fare={}, currency={}, distance={}km, duration={}min", 
            event.getEstimatedFare(), event.getCurrency(), 
            event.getEstimatedDistanceKm(), event.getEstimatedDurationMinutes());
        log.info("pickup=({},{}), address={}", 
            event.getPickupLatitude(), event.getPickupLongitude(), event.getPickupAddress());
        log.info("dropoff=({},{}), address={}", 
            event.getDropoffLatitude(), event.getDropoffLongitude(), event.getDropoffAddress());
        
        // Validate driver exists and is online
        DriverProfile driver = driverProfileRepository.findById(event.getDriverId())
            .orElse(null);
        
        if (driver == null) {
            log.error("Driver {} not found for booking offer {}", event.getDriverId(), event.getBookingId());
            return;
        }
        
        log.info("Driver found: driverId={}, userId={}, status={}, accountStatus={}", 
            driver.getDriverId(), driver.getUserId(), driver.getAvailabilityStatus(), driver.getAccountStatus());
        
        if (driver.getAvailabilityStatus() != DriverAvailabilityStatus.ONLINE) {
            log.warn("Driver {} is not online (status={}), ignoring booking offer {}", 
                event.getDriverId(), driver.getAvailabilityStatus(), event.getBookingId());
            return;
        }
        
        // Check if offer already exists for this booking
        DriverBookingOffer existingOffer = offerRepository
            .findByBookingIdAndDriverIdAndIsActiveTrue(event.getBookingId(), event.getDriverId())
            .orElse(null);
        
        if (existingOffer != null) {
            log.info("Offer already exists for bookingId={}, driverId={}, skipping", 
                event.getBookingId(), event.getDriverId());
            return;
        }
        
        // Create and save booking offer
        DriverBookingOffer offer = new DriverBookingOffer(event.getBookingId(), event.getDriverId());
        offer.setPickupLatitude(event.getPickupLatitude());
        offer.setPickupLongitude(event.getPickupLongitude());
        offer.setDropoffLatitude(event.getDropoffLatitude());
        offer.setDropoffLongitude(event.getDropoffLongitude());
        offer.setVehicleType(event.getVehicleType());
        offer.setEstimatedDistanceKm(event.getEstimatedDistanceKm());
        offer.setEstimatedDurationMinutes(event.getEstimatedDurationMinutes());
        offer.setEstimatedFare(event.getEstimatedFare());
        offer.setCurrency(event.getCurrency() != null ? event.getCurrency() : "VND");
        offer.setPickupAddress(event.getPickupAddress());
        offer.setDropoffAddress(event.getDropoffAddress());
        
        log.info("Saving offer: fare={}, pickup={}, dropoff={}, distance={}km, duration={}min", 
            offer.getEstimatedFare(), offer.getPickupAddress(), offer.getDropoffAddress(),
            offer.getEstimatedDistanceKm(), offer.getEstimatedDurationMinutes());
        
        try {
            offerRepository.save(offer);
            log.info("✓ Successfully saved booking offer for driver {} - bookingId={}, fare={}, expiresAt={}", 
                event.getDriverId(), event.getBookingId(), offer.getEstimatedFare(), offer.getExpiresAt());
            
            // Set userId and publish event for NotificationService to push WebSocket
            event.setUserId(driver.getUserId());
            eventsProducer.publishDriverOfferNotification(event);
            log.info("Published offer to notification topic for userId={}, bookingId={}", 
                driver.getUserId(), event.getBookingId());
            
        } catch (Exception e) {
            log.error("✗ Failed to save booking offer for driver {} - bookingId={}", 
                event.getDriverId(), event.getBookingId(), e);
            e.printStackTrace();
            throw e;
        }
    }
    
    /**
     * Get active booking offers for current driver
     */
    public List<DriverBookingOfferResponse> getActiveOffers(UUID driverId) {
        log.info("=== Getting active offers for driverId={} ===", driverId);
        
        List<DriverBookingOffer> offers = offerRepository
            .findByDriverIdAndIsActiveTrueOrderByOfferedAtDesc(driverId);
        
        log.info("Found {} total offers in DB for driverId={}", offers.size(), driverId);
        
        LocalDateTime now = LocalDateTime.now();
        log.info("Current time: {}", now);
        
        List<DriverBookingOfferResponse> activeOffers = offers.stream()
            .filter(offer -> {
                boolean notExpired = offer.getExpiresAt().isAfter(now);
                log.info("Offer {}: expiresAt={}, notExpired={}, isActive={}", 
                    offer.getBookingId(), offer.getExpiresAt(), notExpired, offer.getIsActive());
                if (!notExpired) {
                    log.warn("Offer {} expired for driverId={} (expiresAt={}, now={})", 
                        offer.getBookingId(), driverId, offer.getExpiresAt(), now);
                }
                return notExpired;
            })
            .map(offer -> {
                long timeLeftSeconds = Duration.between(now, offer.getExpiresAt()).getSeconds();
                
                log.info("Active offer: bookingId={}, driverId={}, vehicleType={}, timeLeft={}s", 
                    offer.getBookingId(), driverId, offer.getVehicleType(), timeLeftSeconds);
                log.info("  -> fare={}, currency={}, distance={}km, duration={}min", 
                    offer.getEstimatedFare(), offer.getCurrency(), 
                    offer.getEstimatedDistanceKm(), offer.getEstimatedDurationMinutes());
                log.info("  -> pickup={}, dropoff={}", 
                    offer.getPickupAddress(), offer.getDropoffAddress());
                
                return DriverBookingOfferResponse.builder()
                    .bookingId(offer.getBookingId())
                    .pickupLatitude(offer.getPickupLatitude())
                    .pickupLongitude(offer.getPickupLongitude())
                    .dropoffLatitude(offer.getDropoffLatitude())
                    .dropoffLongitude(offer.getDropoffLongitude())
                    .vehicleType(offer.getVehicleType())
                    .estimatedDistanceKm(offer.getEstimatedDistanceKm())
                    .estimatedDurationMinutes(offer.getEstimatedDurationMinutes())
                    .estimatedFare(offer.getEstimatedFare())
                    .currency(offer.getCurrency())
                    .pickupAddress(offer.getPickupAddress())
                    .dropoffAddress(offer.getDropoffAddress())
                    .offeredAt(offer.getOfferedAt())
                    .timeLeftSeconds(timeLeftSeconds > 0 ? timeLeftSeconds : 0)
                    .build();
            })
            .collect(Collectors.toList());
        
        log.info("=== Returning {} active offers for driverId={} ===", activeOffers.size(), driverId);
        return activeOffers;
    }
    
    /**
     * Mark offer as inactive (when driver accepts or declines)
     */
    public void deactivateOffer(UUID bookingId, UUID driverId) {
        offerRepository.findByBookingIdAndDriverIdAndIsActiveTrue(bookingId, driverId)
            .ifPresent(offer -> {
                offer.setIsActive(false);
                offerRepository.save(offer);
            });
    }
    
    /**
     * Delete all offers for a booking (when booking is accepted by any driver)
     */
    public void deleteOffersForBooking(UUID bookingId) {
        offerRepository.deleteByBookingId(bookingId);
    }
}



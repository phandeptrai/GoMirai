package com.gomirai.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.tracking.client.BookingServiceClient;
import com.gomirai.tracking.dto.BookingInfoResponse;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.common.dto.event.DriverBookingOfferEvent;
import com.gomirai.tracking.messaging.DriverBookingEventsProducer;
import com.gomirai.tracking.model.BookingSearchState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingSearchService {
    
    private final TrackingService trackingService;
    private final DriverBookingEventsProducer eventsProducer;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final BookingServiceClient bookingServiceClient;
    
    private static final String SEARCH_STATE_KEY_PREFIX = "booking:search:";
    private static final long SEARCH_STATE_TTL_SECONDS = 900; // 15 minutes
    
    @Value("${booking.search.initial-radius-meters:2000}")
    private double initialRadiusMeters;
    
    @Value("${booking.search.radius-expansion-factor:2.0}")
    private double radiusExpansionFactor;
    
    @Value("${booking.search.max-radius-meters:10000}")
    private double maxRadiusMeters;
    
    /**
     * Xử lý sự kiện tìm kiếm tài xế từ BookingService
     * 1. Tìm tài xế gần trong Redis
     * 2. Nếu có, gửi event cho các driver
     * 3. Lưu trạng thái tìm kiếm vào Redis để có thể tăng bán kính sau
     */
    public void handleBookingSearchDrivers(BookingSearchDriversEvent event) {
        UUID bookingId = event.getBookingId();
        
        log.info("=== Handling BookingSearchDriversEvent ===");
        log.info("bookingId={}, pickup=({},{}), vehicleType={}, radius={}m", 
            bookingId, event.getPickupLatitude(), event.getPickupLongitude(), 
            event.getVehicleType(), event.getRadiusMeters());
        
        try {
            // Tạo hoặc cập nhật trạng thái tìm kiếm
            BookingSearchState searchState = getOrCreateSearchState(event);
            log.info("Search state created/updated: currentRadius={}m, attempts={}", 
                searchState.getCurrentRadiusMeters(), searchState.getSearchAttempts());
            
            // Tìm tài xế gần
            List<DriverLocationResponse> nearbyDrivers = findNearbyDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                searchState.getCurrentRadiusMeters(),
                event.getVehicleType()
            );
            
            log.info("Found {} nearby drivers for bookingId={}", nearbyDrivers.size(), bookingId);
            
            if (!nearbyDrivers.isEmpty()) {
                // Có tài xế gần, gửi event cho các driver
                log.info("Sending offers to {} drivers for bookingId={}", nearbyDrivers.size(), bookingId);
                
                int sentCount = 0;
                for (DriverLocationResponse driver : nearbyDrivers) {
                    try {
                        sendBookingOfferToDriver(bookingId, driver, searchState);
                        sentCount++;
                    } catch (Exception e) {
                        log.error("Failed to send offer to driver {} for bookingId={}", 
                            driver.getDriverId(), bookingId, e);
                    }
                }
                
                log.info("Successfully sent {} offers for bookingId={}", sentCount, bookingId);
                
                // Cập nhật trạng thái
                searchState.setLastSearchTime(LocalDateTime.now());
                searchState.setSearchAttempts(searchState.getSearchAttempts() + 1);
                saveSearchState(searchState);
            } else {
                // Không có tài xế, lưu trạng thái để scheduler tăng bán kính sau
                log.warn("No nearby drivers found for bookingId={} within {}m at ({},{}), will retry with expanded radius", 
                    bookingId, searchState.getCurrentRadiusMeters(), 
                    event.getPickupLatitude(), event.getPickupLongitude());
                
                searchState.setLastSearchTime(LocalDateTime.now());
                searchState.setSearchAttempts(searchState.getSearchAttempts() + 1);
                saveSearchState(searchState);
            }
            
        } catch (Exception e) {
            log.error("Error handling BookingSearchDriversEvent for bookingId={}", bookingId, e);
            e.printStackTrace();
        }
    }
    
    /**
     * Tìm tài xế gần sử dụng TrackingService
     */
    private List<DriverLocationResponse> findNearbyDrivers(
            Double latitude, Double longitude, Double radiusMeters, String vehicleType) {
        try {
            NearbyDriverRequest request = new NearbyDriverRequest();
            request.setLatitude(latitude);
            request.setLongitude(longitude);
            request.setRadius(radiusMeters);
            request.setStatus(DriverAvailabilityStatus.ONLINE);
            
            // Parse vehicle type safely
            try {
                request.setVehicleType(VehicleType.valueOf(vehicleType));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid vehicle type: {}, trying to parse", vehicleType);
                // Try to find matching enum
                VehicleType matchedType = null;
                for (VehicleType type : VehicleType.values()) {
                    if (type.name().equalsIgnoreCase(vehicleType)) {
                        matchedType = type;
                        break;
                    }
                }
                if (matchedType == null) {
                    log.error("Cannot parse vehicle type: {}", vehicleType);
                    return List.of();
                }
                request.setVehicleType(matchedType);
            }
            
            request.setLimit(20); // Tối đa 20 tài xế
            
            List<DriverLocationResponse> drivers = trackingService.findNearbyDrivers(request);
            log.info("Found {} nearby drivers for vehicleType={} at ({}, {}) within {}m", 
                drivers.size(), vehicleType, latitude, longitude, radiusMeters);
            
            return drivers;
        } catch (Exception e) {
            log.error("Error finding nearby drivers", e);
            return List.of();
        }
    }
    
    /**
     * Gửi event cho driver để hiển thị popup nhận chuyến
     */
    private void sendBookingOfferToDriver(UUID bookingId, DriverLocationResponse driver, BookingSearchState searchState) {
        try {
            log.info("=== Sending booking offer to driver {} for bookingId={} ===", driver.getDriverId(), bookingId);
            log.info("Search state: fare={}, pickup={}, dropoff={}, distance={}km, duration={}min", 
                searchState.getEstimatedFare(), searchState.getPickupAddress(), 
                searchState.getDropoffAddress(), searchState.getEstimatedDistanceKm(), 
                searchState.getEstimatedDurationMinutes());
            
            DriverBookingOfferEvent event = new DriverBookingOfferEvent(
                bookingId,
                UUID.fromString(driver.getDriverId()),
                searchState.getPickupLatitude(),
                searchState.getPickupLongitude(),
                searchState.getDropoffLatitude(),
                searchState.getDropoffLongitude(),
                searchState.getVehicleType(),
                searchState.getEstimatedDistanceKm(),
                searchState.getEstimatedDurationMinutes(),
                searchState.getEstimatedFare(),
                searchState.getCurrency(),
                searchState.getPickupAddress(),
                searchState.getDropoffAddress()
            );
            
            log.info("Event created: fare={}, pickup={}, dropoff={}", 
                event.getEstimatedFare(), event.getPickupAddress(), event.getDropoffAddress());
            
            eventsProducer.publishDriverBookingOffer(event);
            log.info("✓ Successfully published booking offer to driver {} for bookingId={}", 
                driver.getDriverId(), bookingId);
        } catch (Exception e) {
            log.error("✗ Error sending booking offer to driver {} for bookingId={}", 
                driver.getDriverId(), bookingId, e);
            e.printStackTrace();
        }
    }
    
    /**
     * Lấy hoặc tạo trạng thái tìm kiếm
     */
    private BookingSearchState getOrCreateSearchState(BookingSearchDriversEvent event) {
        String key = SEARCH_STATE_KEY_PREFIX + event.getBookingId();
        String stateJson = redisTemplate.opsForValue().get(key);
        
        if (stateJson != null) {
            try {
                return objectMapper.readValue(stateJson, BookingSearchState.class);
            } catch (Exception e) {
                log.error("Error parsing search state for bookingId={}", event.getBookingId(), e);
            }
        }
        
        // Tạo mới trạng thái
        BookingSearchState state = new BookingSearchState();
        state.setBookingId(event.getBookingId());
        state.setPickupLatitude(event.getPickupLatitude());
        state.setPickupLongitude(event.getPickupLongitude());
        state.setVehicleType(event.getVehicleType());
        state.setCurrentRadiusMeters(event.getRadiusMeters() != null ? event.getRadiusMeters() : initialRadiusMeters);
        state.setSearchAttempts(0);
        state.setSearchStartTime(LocalDateTime.now());
        state.setLastSearchTime(LocalDateTime.now());
        state.setIsActive(true);
        
        // Use data from event first (if available), then try to fetch from BookingService as fallback
        if (event.getDropoffLatitude() != null && event.getDropoffLongitude() != null) {
            // Event contains full booking details
            log.info("Using booking details from event for bookingId={}", event.getBookingId());
            state.setDropoffLatitude(event.getDropoffLatitude());
            state.setDropoffLongitude(event.getDropoffLongitude());
            state.setEstimatedDistanceKm(event.getEstimatedDistanceKm());
            state.setEstimatedDurationMinutes(event.getEstimatedDurationMinutes());
            state.setEstimatedFare(event.getEstimatedFare());
            state.setCurrency(event.getCurrency() != null ? event.getCurrency() : "VND");
            state.setPickupAddress(event.getPickupAddress());
            state.setDropoffAddress(event.getDropoffAddress());
            
            log.info("✓ Set search state from event: fare={}, pickup={}, dropoff={}, distance={}km, duration={}min", 
                state.getEstimatedFare(), state.getPickupAddress(), state.getDropoffAddress(),
                state.getEstimatedDistanceKm(), state.getEstimatedDurationMinutes());
        } else {
            // Fallback: Lấy thông tin booking từ BookingService (for backward compatibility)
            log.info("Event missing booking details, fetching from BookingService for bookingId={}", event.getBookingId());
            try {
                BookingInfoResponse bookingInfo = bookingServiceClient.getBookingInfo(event.getBookingId());
                if (bookingInfo != null) {
                    log.info("✓ Got booking info: fare={}, pickup={}, dropoff={}", 
                        bookingInfo.getEstimatedFare(), bookingInfo.getPickupAddress(), bookingInfo.getDropoffAddress());
                    
                    state.setDropoffLatitude(bookingInfo.getDropoffLatitude());
                    state.setDropoffLongitude(bookingInfo.getDropoffLongitude());
                    state.setEstimatedDistanceKm(bookingInfo.getEstimatedDistanceKm());
                    state.setEstimatedDurationMinutes(bookingInfo.getEstimatedDurationMinutes());
                    state.setEstimatedFare(bookingInfo.getEstimatedFare());
                    state.setCurrency(bookingInfo.getCurrency() != null ? bookingInfo.getCurrency() : "VND");
                    state.setPickupAddress(bookingInfo.getPickupAddress());
                    state.setDropoffAddress(bookingInfo.getDropoffAddress());
                    
                    log.info("✓ Set search state with: fare={}, pickup={}, dropoff={}", 
                        state.getEstimatedFare(), state.getPickupAddress(), state.getDropoffAddress());
                } else {
                    log.warn("✗ Could not fetch booking info for bookingId={}, bookingInfo is null", event.getBookingId());
                    state.setCurrency("VND");
                }
            } catch (Exception e) {
                log.error("✗ Error fetching booking info for bookingId={}", event.getBookingId(), e);
                e.printStackTrace();
                state.setCurrency("VND");
            }
        }
        
        return state;
    }
    
    /**
     * Lưu trạng thái tìm kiếm vào Redis
     */
    private void saveSearchState(BookingSearchState state) {
        try {
            String key = SEARCH_STATE_KEY_PREFIX + state.getBookingId();
            String stateJson = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, stateJson, SEARCH_STATE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error saving search state for bookingId={}", state.getBookingId(), e);
        }
    }
    
    /**
     * Lấy trạng thái tìm kiếm từ Redis
     */
    public BookingSearchState getSearchState(UUID bookingId) {
        try {
            String key = SEARCH_STATE_KEY_PREFIX + bookingId;
            String stateJson = redisTemplate.opsForValue().get(key);
            if (stateJson != null) {
                return objectMapper.readValue(stateJson, BookingSearchState.class);
            }
        } catch (Exception e) {
            log.error("Error getting search state for bookingId={}", bookingId, e);
        }
        return null;
    }
    
    /**
     * Xóa trạng thái tìm kiếm (khi đã có tài xế nhận hoặc hủy)
     */
    public void removeSearchState(UUID bookingId) {
        try {
            String key = SEARCH_STATE_KEY_PREFIX + bookingId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error removing search state for bookingId={}", bookingId, e);
        }
    }
    
    /**
     * Tăng bán kính tìm kiếm và tìm lại
     */
    public void expandSearchRadius(UUID bookingId) {
        BookingSearchState state = getSearchState(bookingId);
        if (state == null || !state.getIsActive()) {
            return;
        }
        
        double newRadius = state.getCurrentRadiusMeters() * radiusExpansionFactor;
        if (newRadius > maxRadiusMeters) {
            newRadius = maxRadiusMeters;
        }
        
        state.setCurrentRadiusMeters(newRadius);
        state.setLastSearchTime(LocalDateTime.now());
        state.setSearchAttempts(state.getSearchAttempts() + 1);
        saveSearchState(state);
        
        log.info("Expanding search radius for bookingId={} to {}m", bookingId, newRadius);
        
        // Tìm lại tài xế với bán kính mới
        List<DriverLocationResponse> nearbyDrivers = findNearbyDrivers(
            state.getPickupLatitude(),
            state.getPickupLongitude(),
            newRadius,
            state.getVehicleType()
        );
        
        if (!nearbyDrivers.isEmpty()) {
            log.info("Found {} nearby drivers for bookingId={} with expanded radius {}m", 
                nearbyDrivers.size(), bookingId, newRadius);
            
            for (DriverLocationResponse driver : nearbyDrivers) {
                sendBookingOfferToDriver(bookingId, driver, state);
            }
        } else {
            log.info("No drivers found for bookingId={} with expanded radius {}m", bookingId, newRadius);
        }
    }
}






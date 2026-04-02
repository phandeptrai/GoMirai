package com.gomirai.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.dto.event.BookingSearchDriversEvent;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.tracking.client.BookingServiceClient;
import com.gomirai.tracking.dto.BookingInfoResponse;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.common.dto.event.DriverBookingOffersBatchEvent;
import com.gomirai.tracking.messaging.DriverBookingEventsProducer;
import com.gomirai.tracking.model.BookingSearchState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private static final String ACTIVE_BOOKINGS_SET_KEY = "booking:search:active"; // Redis Set of active bookingIds
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
        
        log.debug("Handling BookingSearchDriversEvent bookingId={}, pickup=({},{}), vehicleType={}, radius={}m",
            bookingId, event.getPickupLatitude(), event.getPickupLongitude(),
            event.getVehicleType(), event.getRadiusMeters());

        try {
            // Tạo hoặc cập nhật trạng thái tìm kiếm
            BookingSearchState searchState = getOrCreateSearchState(event);
            log.debug("Search state bookingId={}: radius={}m, attempts={}",
                bookingId, searchState.getCurrentRadiusMeters(), searchState.getSearchAttempts());
            
            // Tìm tài xế gần
            List<DriverLocationResponse> nearbyDrivers = findNearbyDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                searchState.getCurrentRadiusMeters(),
                event.getVehicleType()
            );
            
            log.debug("Found {} nearby drivers for bookingId={}", nearbyDrivers.size(), bookingId);
            
            if (!nearbyDrivers.isEmpty()) {
                log.debug("Sending batch offers to {} drivers for bookingId={}", nearbyDrivers.size(), bookingId);

                boolean isExpansion = searchState.getSearchAttempts() > 0;
                publishBookingOffersBatch(bookingId, nearbyDrivers, searchState, isExpansion);
                
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
            log.debug("Found {} nearby drivers for vehicleType={} at ({}, {}) within {}m",
                drivers.size(), vehicleType, latitude, longitude, radiusMeters);
            
            return drivers;
        } catch (Exception e) {
            log.error("Error finding nearby drivers", e);
            return List.of();
        }
    }
    
    /**
     * Single Kafka record for all drivers in this wave (DriverService expands to per-driver logic).
     */
    private void publishBookingOffersBatch(
            UUID bookingId,
            List<DriverLocationResponse> drivers,
            BookingSearchState searchState,
            boolean isExpansion) {
        try {
            List<UUID> driverIds = new ArrayList<>();
            for (DriverLocationResponse d : drivers) {
                try {
                    driverIds.add(UUID.fromString(d.getDriverId()));
                } catch (Exception ex) {
                    log.warn("Skip invalid driverId={} for bookingId={}", d.getDriverId(), bookingId);
                }
            }
            if (driverIds.isEmpty()) {
                return;
            }

            DriverBookingOffersBatchEvent batch = DriverBookingOffersBatchEvent.fromBookingContext(
                    bookingId,
                    driverIds,
                    searchState.getPickupLatitude(),
                    searchState.getPickupLongitude(),
                    searchState.getDropoffLatitude(),
                    searchState.getDropoffLongitude(),
                    searchState.getVehicleType(),
                    searchState.getEstimatedDistanceKm(),
                    searchState.getEstimatedDurationMinutes(),
                    searchState.getEstimatedFare(),
                    searchState.getCurrency() != null ? searchState.getCurrency() : "VND",
                    searchState.getPickupAddress(),
                    searchState.getDropoffAddress(),
                    isExpansion);

            eventsProducer.publishDriverBookingOffersBatch(batch);
        } catch (Exception e) {
            log.error("Failed to publish batch offers for bookingId={}", bookingId, e);
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
        state.setNotifiedDriverIds(new java.util.HashSet<>()); // Initialize empty set
        
        // Use data from event first (if available), then try to fetch from BookingService as fallback
        if (event.getDropoffLatitude() != null && event.getDropoffLongitude() != null) {
            // Event contains full booking details
            log.debug("Using booking details from event for bookingId={}", event.getBookingId());
            state.setDropoffLatitude(event.getDropoffLatitude());
            state.setDropoffLongitude(event.getDropoffLongitude());
            state.setEstimatedDistanceKm(event.getEstimatedDistanceKm());
            state.setEstimatedDurationMinutes(event.getEstimatedDurationMinutes());
            state.setEstimatedFare(event.getEstimatedFare());
            state.setCurrency(event.getCurrency() != null ? event.getCurrency() : "VND");
            state.setPickupAddress(event.getPickupAddress());
            state.setDropoffAddress(event.getDropoffAddress());
            
            log.debug("Set search state from event for bookingId={}", event.getBookingId());
        } else {
            // Fallback: Lấy thông tin booking từ BookingService (for backward compatibility)
            log.debug("Event missing booking details, fetching from BookingService for bookingId={}", event.getBookingId());
            try {
                BookingInfoResponse bookingInfo = bookingServiceClient.getBookingInfo(event.getBookingId());
                if (bookingInfo != null) {
                    log.debug("Got booking info from BookingService for bookingId={}", event.getBookingId());
                    
                    state.setDropoffLatitude(bookingInfo.getDropoffLatitude());
                    state.setDropoffLongitude(bookingInfo.getDropoffLongitude());
                    state.setEstimatedDistanceKm(bookingInfo.getEstimatedDistanceKm());
                    state.setEstimatedDurationMinutes(bookingInfo.getEstimatedDurationMinutes());
                    state.setEstimatedFare(bookingInfo.getEstimatedFare());
                    state.setCurrency(bookingInfo.getCurrency() != null ? bookingInfo.getCurrency() : "VND");
                    state.setPickupAddress(bookingInfo.getPickupAddress());
                    state.setDropoffAddress(bookingInfo.getDropoffAddress());
                    
                    log.debug("Set search state from BookingService for bookingId={}", event.getBookingId());
                } else {
                    log.warn("✗ Could not fetch booking info for bookingId={}, bookingInfo is null", event.getBookingId());
                    state.setCurrency("VND");
                }
            } catch (Exception e) {
                log.error("Error fetching booking info for bookingId={}", event.getBookingId(), e);
                state.setCurrency("VND");
            }
        }
        
        return state;
    }
    
    /**
     * Lưu trạng thái tìm kiếm vào Redis và thêm bookingId vào active set
     */
    private void saveSearchState(BookingSearchState state) {
        try {
            String key = SEARCH_STATE_KEY_PREFIX + state.getBookingId();
            String stateJson = objectMapper.writeValueAsString(state);
            redisTemplate.opsForValue().set(key, stateJson, SEARCH_STATE_TTL_SECONDS, TimeUnit.SECONDS);
            // Track in active set so scheduler can use SMEMBERS instead of KEYS *
            redisTemplate.opsForSet().add(ACTIVE_BOOKINGS_SET_KEY, state.getBookingId().toString());
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
            // Remove from active set
            redisTemplate.opsForSet().remove(ACTIVE_BOOKINGS_SET_KEY, bookingId.toString());
        } catch (Exception e) {
            log.error("Error removing search state for bookingId={}", bookingId, e);
        }
    }
    
    /**
     * Trả về danh sách bookingId đang active (dùng cho scheduler thay vì KEYS *).
     * Dùng SMEMBERS trên một key cụ thể — không scan toàn keyspace như KEYS *
     */
    public java.util.Set<String> getActiveBookingIds() {
        try {
            java.util.Set<String> ids = redisTemplate.opsForSet().members(ACTIVE_BOOKINGS_SET_KEY);
            return ids != null ? ids : java.util.Collections.emptySet();
        } catch (Exception e) {
            log.error("Error getting active booking IDs", e);
            return java.util.Collections.emptySet();
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
        
        log.debug("Expanding search radius for bookingId={} to {}m", bookingId, newRadius);
        
        // Tìm lại tài xế với bán kính mới
        List<DriverLocationResponse> nearbyDrivers = findNearbyDrivers(
            state.getPickupLatitude(),
            state.getPickupLongitude(),
            newRadius,
            state.getVehicleType()
        );
        
        if (!nearbyDrivers.isEmpty()) {
            log.debug("Found {} nearby drivers for bookingId={} with expanded radius {}m",
                    nearbyDrivers.size(), bookingId, newRadius);
            publishBookingOffersBatch(bookingId, nearbyDrivers, state, true);
        } else {
            log.debug("No drivers found for bookingId={} with expanded radius {}m", bookingId, newRadius);
        }
    }
}







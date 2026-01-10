package com.gomirai.tracking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.tracking.model.DriverGeoState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service theo dõi vị trí tài xế theo thời gian thực.
 * 
 * Sử dụng Redis Geo để lưu trữ và tìm kiếm tài xế gần đây:
 * - GEO_KEY ("drivers:geo"): Lưu tọa độ GPS của tài xế
 * - STATE_KEY ("drivers:state:{driverId}"): Lưu metadata (status, vehicleType)
 * với TTL 5 phút
 * 
 * Các chức năng chính:
 * 1. updateLocation: Cập nhật vị trí GPS và metadata của tài xế
 * 2. findNearbyDrivers: Tìm tài xế gần điểm đón trong bán kính cho trước
 * 3. getDriverLocation: Lấy vị trí hiện tại của một tài xế
 * 4. cleanupExpiredGeoPoints: Dọn dẹp Geo Points đã hết hạn
 * 
 * Lưu ý về TTL:
 * - Redis Geo Set không hỗ trợ TTL trực tiếp
 * - Metadata có TTL 5 phút, Geo Point được giữ cho đến khi metadata hết hạn
 * - Scheduled task chạy mỗi 2 phút để dọn dẹp Geo Points có metadata hết hạn
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrackingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String GEO_KEY = "drivers:geo";
    private static final String STATE_KEY_PREFIX = "drivers:state:";
    private static final long STATE_TTL_SECONDS = 300; // 5 minutes

    /**
     * Cập nhật vị trí và metadata của tài xế trong Redis.
     * Nếu Redis gặp sự cố sẽ ném BusinessException để trả lỗi 400 rõ ràng thay vì
     * 500 chung chung.
     */
    public void updateLocation(DriverGeoState state) {
        try {
            // 1. Save Geo Point
            redisTemplate.opsForGeo()
                    .add(GEO_KEY, new Point(state.getLongitude(), state.getLatitude()), state.getDriverId());

            // 2. Save Metadata (Status, VehicleType, lastUpdatedAt, ...)
            saveMetadata(state);

            log.debug("Updated location for driver: {}", state.getDriverId());
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed when updating driver: {}", state.getDriverId(), e);
            throw new BusinessException("Tracking storage is temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Failed to update location for driver: {}", state.getDriverId(), e);
            throw new BusinessException("Failed to update driver location.");
        }
    }

    /**
     * Chỉ cập nhật metadata (status, vehicleType, ...) không động vào GEO.
     * Sử dụng khi nhận DriverAvailabilityChangedEvent từ Kafka.
     */
    public void updateLocationMetadataOnly(DriverGeoState state) {
        try {
            saveMetadata(state);
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed when updating metadata for driver: {}", state.getDriverId(), e);
            throw new BusinessException("Tracking storage is temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Failed to update metadata for driver: {}", state.getDriverId(), e);
            throw new BusinessException("Failed to update driver metadata.");
        }
    }

    private void saveMetadata(DriverGeoState state) throws Exception {
        String stateJson = objectMapper.writeValueAsString(state);
        String key = STATE_KEY_PREFIX + state.getDriverId();
        redisTemplate.opsForValue().set(key, stateJson, STATE_TTL_SECONDS, TimeUnit.SECONDS);

        // Note: Redis Geo Set doesn't support TTL directly.
        // Geo Point will remain until manually removed or when metadata expires.
        // In findNearbyDrivers(), we filter out drivers whose metadata has expired
        // (stateJson == null).
    }

    public List<DriverLocationResponse> findNearbyDrivers(NearbyDriverRequest request) {
        try {
            List<DriverLocationResponse> drivers = new ArrayList<>();
            double currentRadius = request.getRadius();
            int maxAttempts = 3; // Try expanding radius up to 3 times
            double expansionFactor = 2.0;
            List<String> expiredDriverIds = new ArrayList<>(); // Track expired drivers to clean up

            for (int i = 0; i < maxAttempts; i++) {
                // 1. Search in Redis Geo
                Circle circle = new Circle(
                        new Point(request.getLongitude(), request.getLatitude()),
                        new Distance(currentRadius, RedisGeoCommands.DistanceUnit.METERS));
                RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                        .newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(request.getLimit() * 2);

                GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(GEO_KEY,
                        circle, args);

                if (results != null) {
                    for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results) {
                        String driverId = result.getContent().getName();
                        String stateKey = STATE_KEY_PREFIX + driverId;
                        String stateJson = redisTemplate.opsForValue().get(stateKey);

                        if (stateJson != null) {
                            // Metadata exists (not expired)
                            try {
                                DriverGeoState state = objectMapper.readValue(stateJson, DriverGeoState.class);

                                // Filter Logic
                                boolean statusMatch = request.getStatus() == null
                                        || request.getStatus() == state.getStatus();
                                boolean typeMatch = request.getVehicleType() == null
                                        || request.getVehicleType() == state.getVehicleType();

                                if (statusMatch && typeMatch) {
                                    drivers.add(DriverLocationResponse.builder()
                                            .driverId(driverId)
                                            .latitude(result.getContent().getPoint().getY())
                                            .longitude(result.getContent().getPoint().getX())
                                            .status(state.getStatus())
                                            .vehicleType(state.getVehicleType())
                                            .distance(result.getDistance().getValue())
                                            .lastUpdatedAt(state.getLastUpdatedAt())
                                            .build());
                                }
                            } catch (Exception e) {
                                log.warn("Failed to parse state for driver: {}", driverId, e);
                            }
                        } else {
                            // Metadata expired (TTL = 5 minutes) - mark for cleanup
                            expiredDriverIds.add(driverId);
                        }

                        if (drivers.size() >= request.getLimit()) {
                            // Clean up expired Geo Points before returning
                            cleanupExpiredGeoPoints(expiredDriverIds);
                            return drivers; // Found enough drivers
                        }
                    }
                }

                if (!drivers.isEmpty()) {
                    // Clean up expired Geo Points before returning
                    cleanupExpiredGeoPoints(expiredDriverIds);
                    return drivers; // Found some drivers, return them
                }

                // No drivers found, expand radius
                currentRadius *= expansionFactor;
                log.info("No drivers found within {}m, expanding radius to {}m", currentRadius / expansionFactor,
                        currentRadius);
            }

            // Clean up expired Geo Points before returning
            cleanupExpiredGeoPoints(expiredDriverIds);
            return drivers;
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed when searching nearby drivers", e);
            throw new BusinessException("Tracking storage is temporarily unavailable. Please try again later.");
        } catch (Exception e) {
            log.error("Failed to search nearby drivers", e);
            throw new BusinessException("Failed to search nearby drivers.");
        }
    }

    public DriverGeoState getDriverLocation(String driverId) {
        String key = STATE_KEY_PREFIX + driverId;
        String stateJson = redisTemplate.opsForValue().get(key);

        if (stateJson == null) {
            // Metadata expired - also remove Geo Point to prevent memory leak
            redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
            return null;
        }

        try {
            return objectMapper.readValue(stateJson, DriverGeoState.class);
        } catch (Exception e) {
            log.error("Failed to parse state for driver: {}", driverId, e);
            throw new BusinessException("Failed to read driver location.");
        }
    }

    /**
     * Clean up expired Geo Points from Redis Geo Set.
     * Called when metadata has expired (TTL = 5 minutes) to prevent memory leak.
     */
    private void cleanupExpiredGeoPoints(List<String> driverIds) {
        if (driverIds.isEmpty()) {
            return;
        }

        try {
            for (String driverId : driverIds) {
                redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
            }
            log.debug("Cleaned up {} expired Geo Points from Redis", driverIds.size());
        } catch (Exception e) {
            log.warn("Failed to cleanup expired Geo Points", e);
            // Don't throw - cleanup failure shouldn't break the search
        }
    }

    /**
     * Scheduled cleanup task: Remove Geo Points whose metadata has expired.
     * Runs every 2 minutes to prevent memory leak in Redis Geo Set.
     * 
     * Logic:
     * 1. Get all members from Geo Set (Redis Geo Set is implemented as Sorted Set)
     * 2. Check if metadata exists (not expired)
     * 3. If metadata doesn't exist → remove Geo Point
     */
    public void cleanupExpiredGeoPointsScheduled() {
        try {
            // Redis Geo Set is implemented as Sorted Set, so we use ZRANGE to get all
            // members
            // Get all members from the sorted set (0 to -1 means all members)
            var members = redisTemplate.opsForZSet().range(GEO_KEY, 0, -1);
            if (members == null || members.isEmpty()) {
                return;
            }

            int cleanedCount = 0;
            for (String driverId : members) {
                String stateKey = STATE_KEY_PREFIX + driverId;

                // Check if metadata exists (if not, it means TTL expired)
                String stateJson = redisTemplate.opsForValue().get(stateKey);
                if (stateJson == null) {
                    // Metadata expired → remove Geo Point
                    redisTemplate.opsForGeo().remove(GEO_KEY, driverId);
                    cleanedCount++;
                    log.debug("Removed expired Geo Point for driver: {}", driverId);
                }
            }

            if (cleanedCount > 0) {
                log.info("Scheduled cleanup: Removed {} expired Geo Points from Redis", cleanedCount);
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during scheduled cleanup", e);
        } catch (Exception e) {
            log.error("Failed to run scheduled cleanup for expired Geo Points", e);
        }
    }
}

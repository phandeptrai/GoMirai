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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.gomirai.tracking.client.DriverServiceClient;

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
    private final DriverServiceClient driverServiceClient;

    private static final String GEO_KEY = "drivers:geo";
    private static final String STATE_KEY_PREFIX = "drivers:state:";
    private static final long STATE_TTL_SECONDS = 3600; // Increased to 1 hour for Stress Testing stability

    // --- OPTIMIZATION: Micro-cache for nearby results (5s TTL) ---
    // Cache key: quantized lat,lon + radius + type + status
    // FIX: Giảm maximumSize 500 -> 200 để giảm heap footprint (~40% savings)
    private final Cache<String, List<DriverLocationResponse>> nearbyCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(200)
            .build();

    // FIX: Dedicated executor cho async cleanup để tránh ForkJoinPool.commonPool() leaks.
    // Dùng single thread daemon để cleanup tasks không block GC và có thể bị terminate khi idle.
    private final java.util.concurrent.ExecutorService cleanupExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "geo-cleanup");
                t.setDaemon(true);
                return t;
            });

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
        // 1. Try Local Cache first (throttle high-concurrency spikes in same area)
        String cacheKey = String.format("%.4f:%.4f:%.1f:%s:%s", 
                request.getLatitude(), request.getLongitude(), 
                request.getRadius(), request.getVehicleType(), request.getStatus());
        
        List<DriverLocationResponse> cached = nearbyCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.trace("Cache hit for nearby search: {}", cacheKey);
            return cached;
        }

        try {
            List<DriverLocationResponse> drivers = new ArrayList<>();
            double currentRadius = request.getRadius();
            int maxAttempts = 3;
            double expansionFactor = 2.0;

            for (int i = 0; i < maxAttempts; i++) {
                Circle circle = new Circle(
                        new Point(request.getLongitude(), request.getLatitude()),
                        new Distance(currentRadius, RedisGeoCommands.DistanceUnit.METERS));
                RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                        .newGeoRadiusArgs()
                        .includeDistance()
                        .includeCoordinates()
                        .sortAscending()
                        .limit(request.getLimit() * 2);

                GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                        redisTemplate.opsForGeo().radius(GEO_KEY, circle, args);

                if (results != null && !results.getContent().isEmpty()) {
                    // --- FIX: Batch all metadata fetches with one MGET call ---
                    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> resultList = results.getContent();
                    List<String> stateKeys = resultList.stream()
                            .map(r -> STATE_KEY_PREFIX + r.getContent().getName())
                            .collect(Collectors.toList());

                    List<String> stateValues = redisTemplate.opsForValue().multiGet(stateKeys);

                    List<String> expiredDriverIds = new ArrayList<>();
                    for (int j = 0; j < resultList.size(); j++) {
                        GeoResult<RedisGeoCommands.GeoLocation<String>> result = resultList.get(j);
                        String driverId = result.getContent().getName();
                        String stateJson = (stateValues != null) ? stateValues.get(j) : null;

                        if (stateJson != null) {
                            try {
                                DriverGeoState state = objectMapper.readValue(stateJson, DriverGeoState.class);
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
                            expiredDriverIds.add(driverId);
                        }
                    }

                    // --- OPTIMIZATION: Move cleanup to Async thread ---
                    if (!expiredDriverIds.isEmpty()) {
                        // FIX: Dùng cleanupExecutor thay vì ForkJoinPool.commonPool()
                        CompletableFuture.runAsync(() -> cleanupExpiredGeoPoints(expiredDriverIds), cleanupExecutor);
                    }

                    if (drivers.size() >= request.getLimit()) {
                        List<DriverLocationResponse> finalResult = drivers.subList(0, request.getLimit());
                        nearbyCache.put(cacheKey, finalResult);
                        return finalResult;
                    }
                }

                if (!drivers.isEmpty()) {
                    // --- ENRICH: If requested, fetch full details from DriverService in one bulk call ---
                    if (request.isEnrichDetails() && !drivers.isEmpty()) {
                        enrichWithDriverProfile(drivers);
                    }
                    nearbyCache.put(cacheKey, drivers);
                    return drivers;
                }

                currentRadius *= expansionFactor;
                log.debug("No drivers found within {}m, expanding radius to {}m",
                        currentRadius / expansionFactor, currentRadius);
            }

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
     * Clean up expired Geo Points from Redis Geo Set (bulk version).
     * Called after MGET scan when metadata has expired (TTL = 5 minutes).
     * Uses a single remove() call for all expired drivers instead of N individual calls.
     */
    private void cleanupExpiredGeoPoints(List<String> driverIds) {
        if (driverIds.isEmpty()) {
            return;
        }
        try {
            // FIX: Bulk remove in ONE call.
            // Redis Geo Set is a Sorted Set — ZREM is equivalent to GeoOperations.remove().
            // ZSetOperations.remove(K, Object...) has clear Object[] overload.
            redisTemplate.opsForZSet().remove(GEO_KEY, driverIds.toArray());
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
            // --- OPTIMIZATION: Use ZSCAN instead of ZRANGE(0, -1) ---
            // range(0, -1) blocks the entire Redis single thread for O(N).
            // scan works in chunks, allowing other requests to pass through.
            ScanOptions options = ScanOptions.scanOptions().count(100).build();
            Cursor<ZSetOperations.TypedTuple<String>> cursor = redisTemplate.opsForZSet().scan(GEO_KEY, options);
            
            List<String> expiredDriverIds = new ArrayList<>();
            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                String driverId = tuple.getValue();
                
                String stateJson = redisTemplate.opsForValue().get(STATE_KEY_PREFIX + driverId);
                if (stateJson == null) {
                    expiredDriverIds.add(driverId);
                }
                
                // Batch remove to avoid huge ZREM calls
                if (expiredDriverIds.size() >= 100) {
                    redisTemplate.opsForZSet().remove(GEO_KEY, expiredDriverIds.toArray());
                    expiredDriverIds.clear();
                }
            }
            cursor.close();

            if (!expiredDriverIds.isEmpty()) {
                redisTemplate.opsForZSet().remove(GEO_KEY, expiredDriverIds.toArray());
                log.info("Scheduled cleanup: Removed {} remaining expired Geo Points", expiredDriverIds.size());
            }
        } catch (RedisConnectionFailureException e) {
            log.error("Redis connection failed during scheduled cleanup", e);
        } catch (Exception e) {
            log.error("Failed to run scheduled cleanup for expired Geo Points", e);
        }
    }

    private void enrichWithDriverProfile(List<DriverLocationResponse> drivers) {
        try {
            List<UUID> driverIds = drivers.stream()
                    .map(d -> UUID.fromString(d.getDriverId()))
                    .collect(Collectors.toList());

            // Bulk call to DriverService
            List<Object> profiles = driverServiceClient.getProfilesByDriverIds(driverIds);

            if (profiles != null && !profiles.isEmpty()) {
                // Map by ID for fast lookup. DriverProfileResponse contains "driverId"
                Map<String, Object> profileMap = profiles.stream()
                        .collect(Collectors.toMap(
                                p -> {
                                    // Use Jackson to extract driverId property as String
                                    try {
                                        return objectMapper.convertValue(p, Map.class).get("driverId").toString();
                                    } catch (Exception e) {
                                        return "";
                                    }
                                },
                                Function.identity(),
                                (existing, replacement) -> existing));

                drivers.forEach(d -> {
                    Object profile = profileMap.get(d.getDriverId());
                    if (profile != null) {
                        d.setDetails(profile);
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to enrich driver locations with profiles: {}", e.getMessage());
            // Fail gracefully - search results are still valid without enrichment
        }
    }
}

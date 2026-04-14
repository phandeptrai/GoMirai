package com.gomirai.tracking.controller;

import com.gomirai.common.security.SecurityUtils;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.tracking.model.DriverGeoState;
import com.gomirai.tracking.service.TrackingService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

/**
 * Controller theo dõi vị trí tài xế theo thời gian thực.
 * 
 * === DRIVER APIs ===
 * - POST /api/tracking/location: Cập nhật vị trí GPS của tài xế
 * - GET /api/tracking/me: Lấy vị trí hiện tại của chính mình
 * 
 * === CUSTOMER/AUTHENTICATED APIs ===
 * - POST /api/tracking/nearby: Tìm tài xế lân cận (trong bán kính)
 * - GET /api/tracking/drivers/{driverId}: Lấy vị trí của một tài xế cụ thể
 * 
 * Công nghệ sử dụng:
 * - Redis Geo: Lưu trữ và tìm kiếm theo tọa độ GPS
 * - TTL 5 phút: Tài xế không cập nhật vị trí sẽ tự động mất khỏi bản đồ
 */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@Slf4j
public class TrackingController {

    private final TrackingService trackingService;
    private final SecurityUtils securityUtils;

    /**
     * Cập nhật vị trí realtime - chỉ tài xế DRIVER mới được gọi.
     */
    @Bulkhead(name = "trackingRestApi")
    @PostMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<?> updateLocation(@RequestBody @Valid DriverGeoState state) {
        state.setLastUpdatedAt(System.currentTimeMillis());
        trackingService.updateLocation(state);
        return ResponseEntity.ok(Map.of("message", "Location updated successfully"));
    }

    /**
     * Tìm tài xế lân cận - Chỉ hỗ trợ POST (JSON Body cho Mobile App/BookingService).
     */
    @Bulkhead(name = "trackingRestApi")
    @PostMapping("/nearby")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> findNearbyDrivers(@RequestBody @Valid NearbyDriverRequest request) {
        log.debug("Nearby search request: {}", request);
        List<DriverLocationResponse> drivers = trackingService.findNearbyDrivers(request);
        return ResponseEntity.ok(drivers);
    }

    /**
     * Lấy vị trí hiện tại của tài xế - cho Customer tracking trong booking
     * Customer cần xem vị trí driver khi có booking đang active
     */
    @Bulkhead(name = "trackingRestApi")
    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getDriverLocation(@PathVariable String driverId) {
        DriverGeoState state = trackingService.getDriverLocation(driverId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * INTERNAL: Lấy vị trí driver theo driverId bằng Internal API Key.
     *
     * Dùng cho service-to-service calls (BookingService -> TrackingService) trong bối cảnh
     * API Gateway strip JWT và dùng gateway-delegation headers.
     *
     * Auth:
     * - InternalApiKeyFilter sẽ set principal=INTERNAL_SERVICE với authority ROLE_INTERNAL_SERVICE.
     */
    @Bulkhead(name = "trackingRestApi")
    @GetMapping("/internal/drivers/{driverId}")
    @PreAuthorize("hasRole('INTERNAL_SERVICE')")
    public ResponseEntity<?> getDriverLocationInternal(@PathVariable String driverId) {
        DriverGeoState state = trackingService.getDriverLocation(driverId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }

    /**
     * Lấy vị trí hiện tại của chính driver - chỉ DRIVER.
     * Driver chỉ có thể lấy location của chính mình.
     */
    @Bulkhead(name = "trackingRestApi")
    @GetMapping("/me")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<?> getMyLocation() {
        // Get current driver ID from SecurityContext
        String driverId = securityUtils.getCurrentUserId().toString();
        DriverGeoState state = trackingService.getDriverLocation(driverId);
        if (state == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(state);
    }
}

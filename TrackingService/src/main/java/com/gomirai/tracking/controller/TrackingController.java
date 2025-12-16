package com.gomirai.tracking.controller;

import com.gomirai.common.security.SecurityUtils;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.tracking.model.DriverGeoState;
import com.gomirai.tracking.service.TrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingService trackingService;
    private final SecurityUtils securityUtils;

    /**
     * Cập nhật vị trí realtime - chỉ tài xế DRIVER mới được gọi.
     */
    @PostMapping("/location")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<?> updateLocation(@RequestBody @Valid DriverGeoState state) {
        state.setLastUpdatedAt(System.currentTimeMillis());
        trackingService.updateLocation(state);
        return ResponseEntity.ok(Map.of("message", "Location updated successfully"));
    }

    /**
     * Tìm tài xế lân cận - bất kỳ user đã authenticated (CUSTOMER/DRIVER/ADMIN).
     */
    @PostMapping("/nearby")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> findNearbyDrivers(@RequestBody @Valid NearbyDriverRequest request) {
        List<DriverLocationResponse> drivers = trackingService.findNearbyDrivers(request);
        return ResponseEntity.ok(drivers);
    }

    /**
     * Lấy vị trí hiện tại của tài xế - chỉ ADMIN.
     */
    @GetMapping("/drivers/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getDriverLocation(@PathVariable String driverId) {
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

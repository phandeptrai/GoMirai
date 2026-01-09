package com.gomirai.map.controller;

import com.gomirai.map.dto.*;
import com.gomirai.map.service.MapboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller xử lý các API bản đồ (tích hợp Mapbox).
 * 
 * Chức năng: Tính route, geocoding, tìm kiếm địa điểm, ma trận khoảng cách.
 */
@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
@Slf4j
public class MapController {

    private final MapboxService mapboxService;

    /**
     * Calculate route between two points
     * POST /api/map/directions
     * Public endpoint - no authentication required
     */
    @PostMapping("/directions")
    public ResponseEntity<RouteResponse> getDirections(@RequestBody @Valid RouteRequest request) {
        log.info("Calculating route from {} to {}", request.getOrigin(), request.getDestination());
        RouteResponse response = mapboxService.getDirections(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Geocode an address to coordinates
     * GET /api/map/geocode?address=...
     */
    @GetMapping("/geocode")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GeocodeResponse> geocode(@RequestParam String address) {
        log.info("Geocoding address: {}", address);
        GeocodeResponse response = mapboxService.geocode(address);
        return ResponseEntity.ok(response);
    }

    /**
     * Reverse geocode coordinates to address
     * GET /api/map/reverse-geocode?latitude=...&longitude=...
     */
    @GetMapping("/reverse-geocode")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReverseGeocodeResponse> reverseGeocode(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        log.info("Reverse geocoding coordinates: {}, {}", latitude, longitude);
        GeoPoint location = new GeoPoint(latitude, longitude);
        ReverseGeocodeResponse response = mapboxService.reverseGeocode(location);
        return ResponseEntity.ok(response);
    }

    /**
     * Search places by query
     * GET /api/map/places/search?query=...&limit=...&proximity=...
     */
    @GetMapping("/places/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PlaceResponse>> searchPlaces(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "5") Integer limit,
            @RequestParam(required = false) String proximity) {
        log.info("Searching places: query={}, limit={}, proximity={}", query, limit, proximity);
        PlaceSearchRequest request = new PlaceSearchRequest(query, limit, proximity);
        List<PlaceResponse> response = mapboxService.searchPlaces(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Get distance matrix between multiple origins and destinations
     * POST /api/map/distance-matrix
     */
    @PostMapping("/distance-matrix")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DistanceMatrixResponse> getDistanceMatrix(@RequestBody @Valid DistanceMatrixRequest request) {
        log.info("Calculating distance matrix: {} origins, {} destinations",
                request.getOrigins().size(), request.getDestinations().size());
        DistanceMatrixResponse response = mapboxService.getDistanceMatrix(request);
        return ResponseEntity.ok(response);
    }
}

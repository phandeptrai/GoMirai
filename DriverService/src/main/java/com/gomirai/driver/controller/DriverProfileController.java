package com.gomirai.driver.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gomirai.driver.dto.request.DriverApplicationRequest;
import com.gomirai.driver.dto.request.DriverProfileUpdateRequest;
import com.gomirai.driver.dto.request.DriverVehicleRequest;
import com.gomirai.driver.dto.response.DriverBookingOfferResponse;
import com.gomirai.driver.dto.response.DriverProfileResponse;
import com.gomirai.driver.dto.response.DriverRatingResponse;
import com.gomirai.driver.dto.response.DriverStatusResponse;
import com.gomirai.driver.dto.response.DriverVehicleResponse;
import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.common.security.SecurityUtils;
import com.gomirai.driver.service.DriverBookingService;
import com.gomirai.driver.service.DriverProfileService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/drivers")
public class DriverProfileController {

	private final DriverProfileService driverProfileService;
	private final DriverBookingService driverBookingService;
	private final SecurityUtils securityUtils;

	public DriverProfileController(
			DriverProfileService driverProfileService,
			DriverBookingService driverBookingService,
			SecurityUtils securityUtils) {
		this.driverProfileService = driverProfileService;
		this.driverBookingService = driverBookingService;
		this.securityUtils = securityUtils;
	}

	@PostMapping("/apply")
	public ResponseEntity<DriverProfileResponse> apply(@Valid @RequestBody DriverApplicationRequest request) {
		DriverProfileResponse response = driverProfileService.apply(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@GetMapping("/me")
	public ResponseEntity<DriverProfileResponse> me() {
		return ResponseEntity.ok(driverProfileService.getCurrentDriverProfile());
	}

	@PutMapping("/me")
	public ResponseEntity<DriverProfileResponse> updateProfile(
			@Valid @RequestBody DriverProfileUpdateRequest request) {
		return ResponseEntity.ok(driverProfileService.updateCurrentDriver(request));
	}

	@GetMapping("/me/vehicle")
	public ResponseEntity<DriverVehicleResponse> getVehicle() {
		return ResponseEntity.ok(driverProfileService.getCurrentVehicle());
	}

	@PutMapping("/me/vehicle")
	public ResponseEntity<DriverVehicleResponse> updateVehicle(
			@Valid @RequestBody DriverVehicleRequest request) {
		return ResponseEntity.ok(driverProfileService.updateCurrentVehicle(request));
	}

	@PatchMapping("/me/status/online")
	public ResponseEntity<DriverStatusResponse> goOnline() {
		return ResponseEntity.ok(driverProfileService.setAvailabilityOnline());
	}

	@PatchMapping("/me/status/offline")
	public ResponseEntity<DriverStatusResponse> goOffline() {
		return ResponseEntity.ok(driverProfileService.setAvailabilityOffline());
	}

	@GetMapping("/{driverId}/rating")
	public ResponseEntity<DriverRatingResponse> getRating(@PathVariable("driverId") UUID driverId) {
		return ResponseEntity.ok(driverProfileService.getRating(driverId));
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<List<DriverProfileResponse>> listByStatus(
			@RequestParam(value = "status", required = false) DriverAccountStatus status) {
		return ResponseEntity.ok(driverProfileService.listByStatus(status));
	}

	@PatchMapping("/{driverId}/approve")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DriverProfileResponse> approve(@PathVariable UUID driverId) {
		return ResponseEntity.ok(driverProfileService.approve(driverId));
	}

	@PatchMapping("/{driverId}/reject")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DriverProfileResponse> reject(@PathVariable UUID driverId) {
		return ResponseEntity.ok(driverProfileService.reject(driverId));
	}

	@PatchMapping("/{driverId}/suspend")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DriverProfileResponse> suspend(@PathVariable UUID driverId) {
		return ResponseEntity.ok(driverProfileService.suspend(driverId));
	}

	@PatchMapping("/{driverId}/unsuspend")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<DriverProfileResponse> unsuspend(@PathVariable UUID driverId) {
		return ResponseEntity.ok(driverProfileService.unsuspend(driverId));
	}

	/**
	 * Get active booking offers for current driver
	 * GET /api/drivers/me/booking-offers
	 */
	@GetMapping("/me/booking-offers")
	@PreAuthorize("hasRole('DRIVER')")
	public ResponseEntity<List<DriverBookingOfferResponse>> getBookingOffers() {
		UUID userId = securityUtils.getCurrentUserId();
		log.info("=== API: Getting booking offers for userId={} ===", userId);
		
		// Get driverId from DriverProfile using userId
		UUID driverId = driverProfileService.getDriverIdByUserId(userId);
		if (driverId == null) {
			log.warn("No driver profile found for userId={}", userId);
			return ResponseEntity.ok(List.of());
		}
		
		log.info("=== API: Found driverId={} for userId={} ===", driverId, userId);
		List<DriverBookingOfferResponse> offers = driverBookingService.getActiveOffers(driverId);
		log.info("=== API: Returning {} offers for driverId={} (userId={}) ===", offers.size(), driverId, userId);
		if (offers.isEmpty()) {
			log.warn("No offers found for driverId={} (userId={}) - check if events were received and saved", driverId, userId);
		}
		return ResponseEntity.ok(offers);
	}

	/**
	 * Reject/decline a booking offer
	 * PATCH /api/drivers/me/booking-offers/{bookingId}/reject
	 */
	@PatchMapping("/me/booking-offers/{bookingId}/reject")
	@PreAuthorize("hasRole('DRIVER')")
	public ResponseEntity<Void> rejectBookingOffer(@PathVariable UUID bookingId) {
		UUID userId = securityUtils.getCurrentUserId();
		log.info("Driver userId={} rejecting booking offer for bookingId={}", userId, bookingId);
		
		// Get driverId from DriverProfile using userId
		UUID driverId = driverProfileService.getDriverIdByUserId(userId);
		if (driverId == null) {
			log.warn("No driver profile found for userId={}", userId);
			return ResponseEntity.notFound().build();
		}
		
		driverBookingService.deactivateOffer(bookingId, driverId);
		log.info("Successfully deactivated offer for bookingId={}, driverId={}", bookingId, driverId);
		return ResponseEntity.ok().build();
	}
}


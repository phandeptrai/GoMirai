package com.gomirai.driver.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

/**
 * Controller quản lý hồ sơ tài xế.
 * 
 * === ĐĂNG KÝ TÀI XẾ ===
 * - POST /api/drivers/apply: Gửi đơn đăng ký làm tài xế
 * 
 * === DRIVER APIs (tài xế đã được duyệt) ===
 * - GET /api/drivers/me: Lấy thông tin profile của mình
 * - PUT /api/drivers/me: Cập nhật profile
 * - GET /api/drivers/me/vehicle: Lấy thông tin xe
 * - PUT /api/drivers/me/vehicle: Cập nhật thông tin xe
 * - PATCH /api/drivers/me/status/online: Bật chế độ nhận cuốc
 * - PATCH /api/drivers/me/status/offline: Tắt chế độ nhận cuốc
 * - GET /api/drivers/me/booking-offers: Lấy danh sách cuốc đang chờ
 * - PATCH /api/drivers/me/booking-offers/{id}/reject: Từ chối cuốc
 * 
 * === PUBLIC APIs ===
 * - GET /api/drivers/{driverId}: Lấy thông tin tài xế (cho customer xem)
 * - GET /api/drivers/{driverId}/rating: Lấy rating của tài xế
 * - GET /api/drivers/user/{userId}: Lấy profile theo userId
 * 
 * === ADMIN APIs ===
 * - GET /api/drivers: Lấy danh sách tài xế theo status
 * - PATCH /api/drivers/{id}/approve: Duyệt đơn đăng ký
 * - PATCH /api/drivers/{id}/reject: Từ chối đơn đăng ký
 * - PATCH /api/drivers/{id}/suspend: Khóa tài khoản
 * - PATCH /api/drivers/{id}/unsuspend: Mở khóa tài khoản
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/driver")
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

	@GetMapping("/{driverId}")
	public ResponseEntity<DriverProfileResponse> getProfile(@PathVariable("driverId") UUID driverId) {
		return ResponseEntity.ok(driverProfileService.getProfile(driverId));
	}

	/**
	 * Get driver profile by userId (useful when booking stores userId instead of
	 * driverId)
	 * GET /api/drivers/user/{userId}
	 */
	@GetMapping("/user/{userId}")
	public ResponseEntity<DriverProfileResponse> getProfileByUserId(@PathVariable("userId") UUID userId) {
		return ResponseEntity.ok(driverProfileService.getProfileByUserId(userId));
	}

	@PostMapping("/bulk")
	public ResponseEntity<List<DriverProfileResponse>> getProfilesByUserIds(@RequestBody List<UUID> userIds) {
		return ResponseEntity.ok(driverProfileService.getProfilesByUserIds(userIds));
	}

	@PostMapping("/bulk/by-driver-ids")
	public ResponseEntity<List<DriverProfileResponse>> getProfilesByDriverIds(@RequestBody List<UUID> driverIds) {
		return ResponseEntity.ok(driverProfileService.getProfilesByDriverIds(driverIds));
	}

	/**
	 * Get driver public info (aggregated from DriverService + UserService)
	 * Returns: vehicle info, rating, driver name, phone
	 * 
	 * PUBLIC API - for customer to view driver info during trip
	 * GET /api/drivers/user/{userId}/public
	 */
	@GetMapping("/user/{userId}/public")
	public ResponseEntity<com.gomirai.driver.dto.response.DriverPublicInfoResponse> getDriverPublicInfo(
			@PathVariable("userId") UUID userId) {
		return ResponseEntity.ok(driverProfileService.getDriverPublicInfo(userId));
	}

	@GetMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Slice<DriverProfileResponse>> listByStatus(
			@RequestParam(value = "status", required = false) DriverAccountStatus status,
			@PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		log.info("ADMIN listing drivers by status: {}. Page: {}, Size: {}", status, pageable.getPageNumber(), pageable.getPageSize());
		return ResponseEntity.ok(driverProfileService.listByStatus(status, pageable));
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
		// Polled frequently by driver app — keep DEBUG to avoid log spam
		log.trace("GET /me/booking-offers userId={}", userId);

		// Get driverId from DriverProfile using userId
		UUID driverId = driverProfileService.getDriverIdByUserId(userId);
		if (driverId == null) {
			log.warn("No driver profile found for userId={}", userId);
			return ResponseEntity.ok(List.of());
		}

		List<DriverBookingOfferResponse> offers = driverBookingService.getActiveOffers(driverId);
		log.trace("Returning {} offers for driverId={}", offers.size(), driverId);
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
		log.debug("Driver userId={} rejecting booking offer for bookingId={}", userId, bookingId);

		// Get driverId from DriverProfile using userId
		UUID driverId = driverProfileService.getDriverIdByUserId(userId);
		if (driverId == null) {
			log.warn("No driver profile found for userId={}", userId);
			return ResponseEntity.notFound().build();
		}

		driverBookingService.deactivateOffer(bookingId, driverId);
		log.debug("Deactivated offer for bookingId={}, driverId={}", bookingId, driverId);
		return ResponseEntity.ok().build();
	}
}

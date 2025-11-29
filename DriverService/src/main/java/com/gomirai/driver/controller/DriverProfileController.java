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
import com.gomirai.driver.dto.response.DriverProfileResponse;
import com.gomirai.driver.dto.response.DriverRatingResponse;
import com.gomirai.driver.dto.response.DriverStatusResponse;
import com.gomirai.driver.dto.response.DriverVehicleResponse;
import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.driver.service.DriverProfileService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/drivers")
public class DriverProfileController {

	private final DriverProfileService driverProfileService;

	public DriverProfileController(DriverProfileService driverProfileService) {
		this.driverProfileService = driverProfileService;
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
}


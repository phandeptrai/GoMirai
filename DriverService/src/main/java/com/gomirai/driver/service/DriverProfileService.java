package com.gomirai.driver.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.exception.NotFoundException;
import com.gomirai.common.security.SecurityUtils;
import com.gomirai.driver.dto.request.DriverApplicationRequest;
import com.gomirai.driver.dto.request.DriverProfileUpdateRequest;
import com.gomirai.driver.dto.request.DriverVehicleRequest;
import com.gomirai.driver.dto.response.DriverProfileResponse;
import com.gomirai.driver.dto.response.DriverRatingResponse;
import com.gomirai.driver.dto.response.DriverStatusResponse;
import com.gomirai.driver.dto.response.DriverVehicleResponse;
import com.gomirai.driver.enums.DriverAccountStatus;
import com.gomirai.driver.enums.DriverAvailabilityStatus;
import com.gomirai.driver.model.DriverProfile;
import com.gomirai.driver.model.DriverVehicle;
import com.gomirai.driver.repository.DriverProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DriverProfileService {

	private final DriverProfileRepository driverProfileRepository;
	private final SecurityUtils securityUtils;

	public DriverProfileResponse apply(DriverApplicationRequest request) {
		UUID userId = securityUtils.getCurrentUserId();
		DriverProfile existing = driverProfileRepository.findByUserId(userId).orElse(null);

		if (existing != null && existing.getAccountStatus() != DriverAccountStatus.REJECTED) {
			throw new BusinessException("Bạn đã gửi hồ sơ tài xế. Vui lòng chờ xử lý hoặc liên hệ hỗ trợ.");
		}

		DriverProfile profile = existing != null ? existing : new DriverProfile();
		if (profile.getDriverId() == null) {
			profile.setDriverId(UUID.randomUUID());
			profile.setCreatedAt(Instant.now());
		}

		profile.setUserId(userId);
		profile.setLicenseNumber(request.licenseNumber().trim());
		profile.setAccountStatus(DriverAccountStatus.PENDING_VERIFICATION);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setRating(profile.getRating() == null ? 0.0 : profile.getRating());
		profile.setCompletedTrips(profile.getCompletedTrips() == null ? 0 : profile.getCompletedTrips());
		profile.setVehicle(buildVehicle(request));
		profile.setUpdatedAt(Instant.now());

		DriverProfile saved = driverProfileRepository.save(profile);
		return toResponse(saved);
	}

	public DriverProfileResponse getCurrentDriverProfile() {
		DriverProfile profile = getProfileForCurrentUser();
		return toResponse(profile);
	}

	public DriverProfileResponse updateCurrentDriver(DriverProfileUpdateRequest request) {
		DriverProfile profile = getProfileForCurrentUser();
		profile.setLicenseNumber(request.licenseNumber().trim());
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile));
	}

	public DriverVehicleResponse getCurrentVehicle() {
		DriverProfile profile = getProfileForCurrentUser();
		if (profile.getVehicle() == null) {
			throw new NotFoundException("Chưa có thông tin xe. Vui lòng cập nhật trước.");
		}
		return toVehicleResponse(profile.getVehicle());
	}

	public DriverVehicleResponse updateCurrentVehicle(DriverVehicleRequest request) {
		DriverProfile profile = getProfileForCurrentUser();
		profile.setVehicle(buildVehicle(request));
		profile.setUpdatedAt(Instant.now());
		driverProfileRepository.save(profile);
		return toVehicleResponse(profile.getVehicle());
	}

	public DriverStatusResponse setAvailabilityOnline() {
		return updateAvailability(true);
	}

	public DriverStatusResponse setAvailabilityOffline() {
		return updateAvailability(false);
	}

	public DriverRatingResponse getRating(UUID driverId) {
		DriverProfile profile = driverProfileRepository.findById(driverId)
			.orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
		return new DriverRatingResponse(profile.getDriverId(), profile.getRating());
	}

	public List<DriverProfileResponse> listByStatus(DriverAccountStatus status) {
		List<DriverProfile> profiles = status == null
			? driverProfileRepository.findAll()
			: driverProfileRepository.findByAccountStatus(status);
		return profiles.stream()
			.map(this::toResponse)
			.toList();
	}

	public DriverProfileResponse approve(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		if (profile.getAccountStatus() == DriverAccountStatus.ACTIVE) {
			return toResponse(profile);
		}
		if (profile.getAccountStatus() == DriverAccountStatus.BANNED) {
			throw new BusinessException("Không thể duyệt tài xế đang bị khóa. Hãy mở khóa trước.");
		}
		profile.setAccountStatus(DriverAccountStatus.ACTIVE);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile));
	}

	public DriverProfileResponse reject(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		profile.setAccountStatus(DriverAccountStatus.REJECTED);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile));
	}

	public DriverProfileResponse suspend(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		profile.setAccountStatus(DriverAccountStatus.BANNED);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile));
	}

	public DriverProfileResponse unsuspend(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		if (profile.getAccountStatus() != DriverAccountStatus.BANNED) {
			throw new BusinessException("Tài xế không nằm trong trạng thái bị khóa.");
		}
		profile.setAccountStatus(DriverAccountStatus.ACTIVE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile));
	}

	private DriverStatusResponse updateAvailability(boolean online) {
		DriverProfile profile = getProfileForCurrentUser();
		if (profile.getAccountStatus() != DriverAccountStatus.ACTIVE) {
			throw new BusinessException("Chỉ tài khoản đã duyệt mới được thay đổi trạng thái nhận cuốc.");
		}
		profile.setAvailabilityStatus(online ? DriverAvailabilityStatus.ONLINE : DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		driverProfileRepository.save(profile);
		return new DriverStatusResponse(profile.getDriverId(), profile.getAccountStatus(), profile.getAvailabilityStatus());
	}

	private DriverProfile getProfileForCurrentUser() {
		UUID userId = securityUtils.getCurrentUserId();
		DriverProfile profile = driverProfileRepository.findByUserId(userId)
			.orElseThrow(() -> new NotFoundException("Chưa tìm thấy hồ sơ tài xế cho người dùng hiện tại."));
		return profile;
	}

	private DriverProfile getRequiredProfile(UUID driverId) {
		return driverProfileRepository.findById(driverId)
			.orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
	}

	private DriverVehicle buildVehicle(DriverApplicationRequest request) {
		return DriverVehicle.builder()
			.vehicleId(UUID.randomUUID())
			.brand(request.vehicleBrand().trim())
			.model(request.vehicleModel().trim())
			.plateNumber(request.plateNumber().trim().toUpperCase())
			.color(request.color().trim())
			.type(request.vehicleType())
			.registrationDate(request.registrationDate())
			.build();
	}

	private DriverVehicle buildVehicle(DriverVehicleRequest request) {
		return DriverVehicle.builder()
			.vehicleId(UUID.randomUUID())
			.brand(request.brand().trim())
			.model(request.model().trim())
			.plateNumber(request.plateNumber().trim().toUpperCase())
			.color(request.color().trim())
			.type(request.type())
			.registrationDate(request.registrationDate())
			.build();
	}

	private DriverProfileResponse toResponse(DriverProfile profile) {
		return new DriverProfileResponse(
			profile.getDriverId(),
			profile.getUserId(),
			profile.getLicenseNumber(),
			profile.getAccountStatus(),
			profile.getAvailabilityStatus(),
			profile.getRating(),
			profile.getCompletedTrips(),
			profile.getVehicle() == null ? null : toVehicleResponse(profile.getVehicle()),
			profile.getCreatedAt(),
			profile.getUpdatedAt()
		);
	}

	private DriverVehicleResponse toVehicleResponse(DriverVehicle vehicle) {
		return new DriverVehicleResponse(
			vehicle.getVehicleId(),
			vehicle.getBrand(),
			vehicle.getModel(),
			vehicle.getPlateNumber(),
			vehicle.getColor(),
			vehicle.getType(),
			vehicle.getRegistrationDate()
		);
	}
}


package com.gomirai.driver.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import com.gomirai.common.dto.event.DriverAvailabilityChangedEvent;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.common.exception.NotFoundException;
import com.gomirai.common.security.SecurityUtils;
import com.gomirai.driver.dto.request.DriverApplicationRequest;
import com.gomirai.driver.dto.request.DriverProfileUpdateRequest;
import com.gomirai.driver.dto.request.DriverVehicleRequest;
import com.gomirai.driver.dto.response.DriverProfileResponse;
import com.gomirai.driver.dto.response.DriverPublicInfoResponse;
import com.gomirai.driver.dto.response.DriverRatingResponse;
import com.gomirai.driver.dto.response.DriverStatusResponse;
import com.gomirai.driver.dto.response.DriverVehicleResponse;
import com.gomirai.common.dto.event.DriverApprovedEvent;
import com.gomirai.common.enums.DriverAccountStatus;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.driver.messaging.DriverAvailabilityEventsProducer;
import com.gomirai.driver.messaging.DriverApprovalEventsProducer;
import com.gomirai.driver.model.DriverProfile;
import com.gomirai.driver.model.DriverVehicle;
import com.gomirai.driver.repository.DriverProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service quản lý hồ sơ tài xế.
 * 
 * Các chức năng chính:
 * 1. apply: Tài xế gửi đơn đăng ký (hồ sơ + thông tin xe)
 * 2. approve/reject: Admin duyệt/từ chối đơn đăng ký
 * 3. setAvailabilityOnline/Offline: Tài xế bật/tắt chế độ nhận cuốc
 * 4. suspend/unsuspend: Admin khóa/mở khóa tài khoản tài xế
 * 
 * Vòng đời tài xế (Account Status):
 * - PENDING_VERIFICATION: Đã gửi đơn, chờ admin duyệt
 * - ACTIVE: Đã được duyệt, có thể nhận cuốc
 * - REJECTED: Bị từ chối, có thể nộp lại đơn
 * - BANNED: Bị khóa, cần admin mở khóa
 * 
 * Trạng thái nhận cuốc (Availability Status):
 * - ONLINE: Đang sẵn sàng nhận cuốc (hiển thị trên bản đồ)
 * - OFFLINE: Không nhận cuốc
 * - BUSY: Đang trong chuyến đi
 * 
 * Event-Driven:
 * - Khi duyệt đơn → publish DriverApprovedEvent → AuthService cập nhật role
 * - Khi đổi trạng thái → publish DriverAvailabilityChangedEvent →
 * TrackingService cập nhật Redis
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DriverProfileService {

	private final DriverProfileRepository driverProfileRepository;
	private final SecurityUtils securityUtils;
	private final DriverAvailabilityEventsProducer availabilityEventsProducer;
	private final DriverApprovalEventsProducer approvalEventsProducer;
	private final com.gomirai.driver.client.UserServiceClient userServiceClient;
	private final com.gomirai.driver.client.ReviewServiceClient reviewServiceClient;

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
		return toResponse(saved, true);
	}

	public DriverProfileResponse getCurrentDriverProfile() {
		DriverProfile profile = getProfileForCurrentUser();
		DriverProfileResponse response = toResponse(profile, true);

		// Fetch Rating & Review stats direct from ReviewService for accuracy
		try {
			// Reuse Feign Client logic
			var ratingSummary = reviewServiceClient.getRatingSummary(profile.getUserId());
			if (ratingSummary != null) {
				return new DriverProfileResponse(
						response.driverId(),
						response.userId(),
						response.licenseNumber(),
						response.accountStatus(),
						response.availabilityStatus(),
						ratingSummary.getAverageRating(),
						ratingSummary.getTotalReviews(), // Mapped to completedTrips because frontend uses this field
						response.vehicle(),
						response.createdAt(),
						response.updatedAt());
			}
		} catch (Exception e) {
			// Fallback to local DB data if service fails
		}
		return response;
	}

	public DriverProfileResponse updateCurrentDriver(DriverProfileUpdateRequest request) {
		DriverProfile profile = getProfileForCurrentUser();
		profile.setLicenseNumber(request.licenseNumber().trim());
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile), true);
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

	public DriverProfileResponse getProfile(UUID driverId) {
		DriverProfile profile = driverProfileRepository.findById(driverId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế: " + driverId));
		return toResponse(profile, true);
	}

	public DriverProfileResponse getProfileByUserId(UUID userId) {
		DriverProfile profile = driverProfileRepository.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế cho userId: " + userId));
		return toResponse(profile, true);
	}

	public List<DriverProfileResponse> getProfilesByUserIds(List<UUID> userIds) {
		List<DriverProfile> profiles = driverProfileRepository.findAllByUserIdIn(userIds);
		return profiles.stream()
				.map(p -> toResponse(p, false))
				.toList();
	}

	public List<DriverProfileResponse> getProfilesByDriverIds(List<UUID> driverIds) {
		Iterable<DriverProfile> profiles = driverProfileRepository.findAllById(driverIds);
		java.util.List<DriverProfile> profileList = new java.util.ArrayList<>();
		profiles.forEach(profileList::add);
		return profileList.stream()
				.map(p -> toResponse(p, false))
				.toList();
	}

	/**
	 * Lấy thông tin public của tài xế (dành cho customer xem).
	 * Tổng hợp từ DriverProfile, UserService (tên, số điện thoại) và ReviewService
	 * (rating).
	 * 
	 * @param userId - userId của tài xế
	 * @return DriverPublicInfoResponse bao gồm vehicle, rating, tên và số điện
	 *         thoại
	 */
	public DriverPublicInfoResponse getDriverPublicInfo(UUID userId) {
		DriverProfile profile = driverProfileRepository.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("Không tìm thấy tài xế cho userId: " + userId));

		// Sử dụng CompletableFuture để gọi song song 2 services
		// 1. Task lấy User Info
		CompletableFuture<com.gomirai.driver.client.dto.UserProfileResponse> userFuture = CompletableFuture
				.supplyAsync(() -> {
					try {
						return userServiceClient.getUserPublicInfo(userId);
					} catch (Exception e) {
						// Log for debug but return null to proceed
						return null;
					}
				});

		// 2. Task lấy Rating Info
		CompletableFuture<com.gomirai.driver.dto.response.RatingSummaryResponse> ratingFuture = CompletableFuture
				.supplyAsync(() -> {
					try {
						return reviewServiceClient.getRatingSummary(userId);
					} catch (Exception e) {
						return null;
					}
				});

		// Chờ cả 2 xong
		CompletableFuture.allOf(userFuture, ratingFuture).join();

		// Xử lý kết quả User Info
		String fullName = null;
		String phone = null;
		try {
			var userInfo = userFuture.get();
			if (userInfo != null) {
				fullName = userInfo.fullName();
				phone = userInfo.phone();
			}
		} catch (Exception e) {
		}

		// Xử lý kết quả Rating Info
		Double realRating = profile.getRating();
		Integer totalReviews = profile.getCompletedTrips();
		try {
			var ratingSummary = ratingFuture.get();
			if (ratingSummary != null) {
				realRating = ratingSummary.getAverageRating();
				totalReviews = ratingSummary.getTotalReviews();
			}
		} catch (Exception e) {
		}

		return new DriverPublicInfoResponse(
				profile.getDriverId(),
				profile.getUserId(),
				realRating,
				totalReviews,
				profile.getAvailabilityStatus(),
				profile.getVehicle() == null ? null : toVehicleResponse(profile.getVehicle()),
				fullName,
				phone);
	}

	public Slice<DriverProfileResponse> listByStatus(DriverAccountStatus status, Pageable pageable) {
		Slice<DriverProfile> profiles = status == null
				? driverProfileRepository.findAllBy(pageable)
				: driverProfileRepository.findByAccountStatus(status, pageable);
		
		log.info("ADMIN found {} drivers in current slice for status: {}", profiles.getNumberOfElements(), status);
		
		// Dùng false để không gọi ReviewService cho từng item trong list (tránh N+1)
		return profiles.map(p -> toResponse(p, false));
	}

	public DriverProfileResponse approve(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		if (profile.getAccountStatus() == DriverAccountStatus.ACTIVE) {
			return toResponse(profile, true);
		}
		if (profile.getAccountStatus() == DriverAccountStatus.BANNED) {
			throw new BusinessException("Không thể duyệt tài xế đang bị khóa. Hãy mở khóa trước.");
		}
		profile.setAccountStatus(DriverAccountStatus.ACTIVE);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());

		DriverProfile saved = driverProfileRepository.save(profile);

		approvalEventsProducer.publishDriverApproved(
				new DriverApprovedEvent(
						saved.getUserId(),
						saved.getDriverId(),
						Instant.now().toString()));

		return toResponse(saved, true);
	}

	public DriverProfileResponse reject(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		profile.setAccountStatus(DriverAccountStatus.REJECTED);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile), true);
	}

	public DriverProfileResponse suspend(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		profile.setAccountStatus(DriverAccountStatus.BANNED);
		profile.setAvailabilityStatus(DriverAvailabilityStatus.OFFLINE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile), true);
	}

	public DriverProfileResponse unsuspend(UUID driverId) {
		DriverProfile profile = getRequiredProfile(driverId);
		if (profile.getAccountStatus() != DriverAccountStatus.BANNED) {
			throw new BusinessException("Tài xế không nằm trong trạng thái bị khóa.");
		}
		profile.setAccountStatus(DriverAccountStatus.ACTIVE);
		profile.setUpdatedAt(Instant.now());
		return toResponse(driverProfileRepository.save(profile), true);
	}

	private DriverStatusResponse updateAvailability(boolean online) {
		DriverProfile profile = getProfileForCurrentUser();
		if (profile.getAccountStatus() != DriverAccountStatus.ACTIVE) {
			throw new BusinessException("Chỉ tài khoản đã duyệt mới được thay đổi trạng thái nhận cuốc.");
		}
		DriverAvailabilityStatus newStatus = online ? DriverAvailabilityStatus.ONLINE
				: DriverAvailabilityStatus.OFFLINE;
		profile.setAvailabilityStatus(newStatus);
		profile.setUpdatedAt(Instant.now());
		driverProfileRepository.save(profile);

		availabilityEventsProducer.publishAvailabilityChanged(
				new DriverAvailabilityChangedEvent(
						profile.getDriverId(),
						newStatus,
						profile.getVehicle() != null ? profile.getVehicle().getType() : null));

		return new DriverStatusResponse(profile.getDriverId(), profile.getAccountStatus(),
				profile.getAvailabilityStatus());
	}

	private DriverProfile getProfileForCurrentUser() {
		UUID userId = securityUtils.getCurrentUserId();
		DriverProfile profile = driverProfileRepository.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("Chưa tìm thấy hồ sơ tài xế cho người dùng hiện tại."));
		return profile;
	}

	public UUID getDriverIdByUserId(UUID userId) {
		return driverProfileRepository.findByUserId(userId)
				.map(DriverProfile::getDriverId)
				.orElse(null);
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

	private DriverProfileResponse toResponse(DriverProfile profile, boolean fetchRealtimeRating) {
		Double finalRating = profile.getRating();
		Integer finalTrips = profile.getCompletedTrips();

		if (fetchRealtimeRating) {
			try {
				var ratingSummary = reviewServiceClient.getRatingSummary(profile.getUserId());
				if (ratingSummary != null) {
					finalRating = ratingSummary.getAverageRating();
					finalTrips = ratingSummary.getTotalReviews();
				}
			} catch (Exception e) {
				log.warn("Failed to fetch realtime rating for driver {}: {}", profile.getUserId(), e.getMessage());
			}
		}

		return new DriverProfileResponse(
				profile.getDriverId(),
				profile.getUserId(),
				profile.getLicenseNumber(),
				profile.getAccountStatus(),
				profile.getAvailabilityStatus(),
				finalRating,
				finalTrips,
				profile.getVehicle() == null ? null : toVehicleResponse(profile.getVehicle()),
				profile.getCreatedAt(),
				profile.getUpdatedAt());
	}

	private DriverVehicleResponse toVehicleResponse(DriverVehicle vehicle) {
		return new DriverVehicleResponse(
				vehicle.getVehicleId(),
				vehicle.getBrand(),
				vehicle.getModel(),
				vehicle.getPlateNumber(),
				vehicle.getColor(),
				vehicle.getType(),
				vehicle.getRegistrationDate());
	}
}

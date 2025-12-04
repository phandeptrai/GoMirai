package com.gomirai.tracking.consumer;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.dto.event.DriverAvailabilityChangedEvent;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.tracking.model.DriverGeoState;
import com.gomirai.tracking.service.TrackingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Consumer lắng nghe sự kiện thay đổi trạng thái tài xế từ DriverService
 * và đồng bộ trạng thái vào Redis (sử dụng cho việc filter trong nearby search).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverAvailabilityChangedConsumer {

	private final TrackingService trackingService;
	private final ObjectMapper objectMapper;

	@KafkaListener(
		topics = "${kafka.topic.driver-availability-changed:driver-availability-changed}",
		groupId = "${spring.kafka.consumer.group-id:tracking-service-group}"
	)
	public void handleDriverAvailabilityChanged(DriverAvailabilityChangedEvent event) {
		try {
			event.validate();

			log.info("Received DriverAvailabilityChangedEvent: driverId={}, status={}, vehicleType={}",
				event.driverId(), event.availabilityStatus(), event.vehicleType());

			// Lấy state hiện tại nếu có, hoặc tạo mới skeleton
			DriverGeoState existing = trackingService.getDriverLocation(event.driverId().toString());
			if (existing == null) {
				existing = new DriverGeoState();
				existing.setDriverId(event.driverId().toString());
				// lat/lon sẽ được cập nhật khi driver bắn location
			}

			existing.setStatus(event.availabilityStatus());
			existing.setVehicleType(event.vehicleType());
			// Không thay đổi lastUpdatedAt ở đây; sẽ cập nhật khi có location

			// Ghi lại metadata (không thay đổi GEO nếu chưa có location)
			trackingService.updateLocationMetadataOnly(existing);
		} catch (RedisConnectionFailureException e) {
			log.error("Redis connection failed when handling DriverAvailabilityChangedEvent for driverId={}",
				event.driverId(), e);
			throw new BusinessException("Tracking storage is temporarily unavailable. Please try again later.");
		} catch (Exception e) {
			log.error("Failed to handle DriverAvailabilityChangedEvent for driverId={}", event.driverId(), e);
			throw new BusinessException("Failed to sync driver availability to tracking.");
		}
	}
}





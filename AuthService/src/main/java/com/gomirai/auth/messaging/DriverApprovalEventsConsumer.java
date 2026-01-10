package com.gomirai.auth.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverApprovedEvent;
import com.gomirai.auth.service.AuthApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Consumer xử lý sự kiện duyệt tài xế.
 * 
 * Luồng xử lý:
 * 1. UserService gửi event DriverApproved khi admin duyệt đơn đăng ký tài xế
 * 2. Consumer này nhận event và cập nhật role của user trong AuthUser
 * 3. User cần refresh token để có JWT mới với role DRIVER
 * 
 * Kiến trúc Event-Driven:
 * - Loosely coupled: AuthService không phụ thuộc trực tiếp vào UserService
 * - Async: Việc cập nhật role không block luồng duyệt đơn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverApprovalEventsConsumer {

	private final AuthApplicationService authApplicationService;

	/**
	 * Handler nhận và xử lý event DriverApproved.
	 * 
	 * @param event Chứa userId và driverId của tài xế được duyệt
	 */
	@KafkaListener(topics = "driver-approval-events", groupId = "auth-service")
	public void handleDriverApproved(DriverApprovedEvent event) {
		log.info("Nhận event DriverApproved cho userId={}, driverId={}", event.userId(), event.driverId());
		try {
			// Cập nhật role từ CUSTOMER thành DRIVER trong database
			authApplicationService.updateUserRoleToDriver(event.userId());
			log.info("Đã cập nhật role thành DRIVER cho userId={}", event.userId());
		} catch (Exception e) {
			// Log lỗi nhưng không throw để không block Kafka consumer
			// Có thể implement retry mechanism hoặc dead letter queue
			log.error("Lỗi khi cập nhật role cho userId={}: {}", event.userId(), e.getMessage(), e);
		}
	}
}

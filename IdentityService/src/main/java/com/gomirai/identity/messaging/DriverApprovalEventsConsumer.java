package com.gomirai.identity.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverApprovedEvent;
import com.gomirai.identity.service.AuthApplicationService;

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
	private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

	/**
	 * Handler nhận và xử lý event DriverApproved.
	 */
	@KafkaListener(topics = "driver-approval-events", groupId = "${spring.kafka.consumer.group-id:auth-service-group}")
	public void handleDriverApproved(String payload) {
		log.info("Nhận JSON event DriverApproved: {}", payload);
		try {
			// Giải mã thủ công để tránh lỗi Deserialization Record
			DriverApprovedEvent event = objectMapper.readValue(payload, DriverApprovedEvent.class);
			
			log.info("Parsed event: userId={}, driverId={}", event.userId(), event.driverId());
			
			// Cập nhật role từ CUSTOMER thành DRIVER trong database
			authApplicationService.updateUserRoleToDriver(event.userId());
			log.info("Đã cập nhật role thành DRIVER cho userId={}", event.userId());
		} catch (Exception e) {
			log.error("Lỗi khi xử lý event DriverApproved payload={}: {}", payload, e.getMessage());
		}
	}
}

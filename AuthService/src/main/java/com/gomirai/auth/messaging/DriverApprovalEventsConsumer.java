package com.gomirai.auth.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverApprovedEvent;
import com.gomirai.auth.service.AuthApplicationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverApprovalEventsConsumer {

	private final AuthApplicationService authApplicationService;

	@KafkaListener(topics = "driver-approval-events", groupId = "auth-service")
	public void handleDriverApproved(DriverApprovedEvent event) {
		log.info("Received DriverApproved event for userId={}, driverId={}", event.userId(), event.driverId());
		try {
			authApplicationService.updateUserRoleToDriver(event.userId());
			log.info("Successfully updated user role to DRIVER for userId={}", event.userId());
		} catch (Exception e) {
			log.error("Failed to update user role for userId={}: {}", event.userId(), e.getMessage(), e);
		}
	}
}

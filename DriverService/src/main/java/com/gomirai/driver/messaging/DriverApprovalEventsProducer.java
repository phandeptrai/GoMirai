package com.gomirai.driver.messaging;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverApprovedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverApprovalEventsProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;
	private static final String TOPIC = "driver-approval-events";

	public void publishDriverApproved(DriverApprovedEvent event) {
		log.info("Publishing DriverApproved event for userId={}, driverId={}", event.userId(), event.driverId());
		kafkaTemplate.send(TOPIC, event.userId().toString(), event);
	}
}

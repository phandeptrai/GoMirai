package com.gomirai.driver.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverAvailabilityChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class DriverAvailabilityEventsProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${kafka.topic.driver-availability-changed:driver-availability-changed}")
	private String topicName;

	public void publishAvailabilityChanged(DriverAvailabilityChangedEvent event) {
		try {
			event.validate();
			kafkaTemplate.send(topicName, event.driverId().toString(), event);
			log.info("Published DriverAvailabilityChangedEvent: driverId={}, status={}, vehicleType={}",
				event.driverId(), event.availabilityStatus(), event.vehicleType());
		} catch (Exception e) {
			log.error("Failed to publish DriverAvailabilityChangedEvent for driverId={}", event.driverId(), e);
		}
	}
}





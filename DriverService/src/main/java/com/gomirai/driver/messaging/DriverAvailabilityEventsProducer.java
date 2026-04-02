package com.gomirai.driver.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.gomirai.common.dto.event.DriverAvailabilityChangedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Producer gửi sự kiện khi tài xế thay đổi trạng thái nhận cuốc.
 * 
 * Topic: driver-availability-changed
 * Consumer: TrackingService (cập nhật metadata trong Redis Geo)
 * 
 * Events: ONLINE → OFFLINE hoặc ngược lại
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DriverAvailabilityEventsProducer {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Value("${kafka.topic.driver-availability-changed:driver-availability-changed}")
	private String topicName;

	/**
	 * Publish sự kiện tài xế thay đổi trạng thái nhận cuốc
	 * 
	 * Consumer: TrackingService (DriverAvailabilityChangedConsumer)
	 * Mục đích: Cập nhật metadata trong Redis GEO (ONLINE/OFFLINE/ON_TRIP)
	 * 
	 * Trigger: Driver bật/tắt chế độ nhận cuốc, bắt đầu/kết thúc chuyến đi
	 */
	public void publishAvailabilityChanged(DriverAvailabilityChangedEvent event) {
		try {
			event.validate();
			kafkaTemplate.send(topicName, event.driverId().toString(), event);
			log.debug("Published DriverAvailabilityChangedEvent: driverId={}, status={}, vehicleType={}",
					event.driverId(), event.availabilityStatus(), event.vehicleType());
		} catch (Exception e) {
			log.error("Failed to publish DriverAvailabilityChangedEvent for driverId={}", event.driverId(), e);
		}
	}
}

package com.gomirai.payment.messaging;

import com.gomirai.common.dto.event.PaymentResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventsProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.payment-result:payment-result-event}")
    private String paymentResultTopic;

    public void publishPaymentResult(PaymentResultEvent event) {
        try {
            kafkaTemplate.send(paymentResultTopic, event.getBookingId().toString(), event);
            log.info("Published PaymentResultEvent: bookingId={}, status={}",
                    event.getBookingId(), event.getStatus());
        } catch (Exception e) {
            log.error("Failed to publish PaymentResultEvent for bookingId={}",
                    event.getBookingId(), e);
        }
    }
}

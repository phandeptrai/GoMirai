package com.gomirai.payment.messaging;

import com.gomirai.common.dto.event.BookingCreatedEvent;
import com.gomirai.common.dto.event.PaymentResultEvent;
import com.gomirai.payment.dto.request.RidePaymentRequest;
import com.gomirai.payment.dto.response.TransactionResponse;
import com.gomirai.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingCreatedConsumer {

    private final WalletService walletService;
    private final PaymentEventsProducer paymentEventsProducer;

    @KafkaListener(topics = "${kafka.topic.booking-created:booking-created-event}", groupId = "${spring.kafka.consumer.group-id}")
    public void handleBookingCreated(BookingCreatedEvent event, Acknowledgment acknowledgment) {
        log.info("RECEIVED BookingCreatedEvent: bookingId={}, paymentMethod={}",
                event.getBookingId(), event.getPaymentMethod());

        // Nếu thanh toán bằng ví (WALLET), thực hiện trừ tiền ngay
        if ("WALLET".equalsIgnoreCase(event.getPaymentMethod())) {
            processWalletPayment(event);
        } else {
            // Trường hợp CASH hoặc khác: Luôn là SUCCESS vì không cần trừ tiền trước
            publishSuccess(event.getBookingId(), event.getCustomerId(), "CASH_NO_TRANS");
        }

        acknowledgment.acknowledge();
    }

    private void processWalletPayment(BookingCreatedEvent event) {
        try {
            RidePaymentRequest request = new RidePaymentRequest(
                    event.getCustomerId(),
                    event.getBookingId(),
                    event.getAmount());

            TransactionResponse response = walletService.payRide(request);

            if ("SUCCESS".equalsIgnoreCase(response.status())) {
                publishSuccess(event.getBookingId(), event.getCustomerId(), response.transactionId().toString());
            } else {
                publishFailure(event.getBookingId(), event.getCustomerId(), "PAYMENT_FAILED",
                        "Unable to process payment");
            }
        } catch (Exception e) {
            log.error("WALLET_PAYMENT_ERROR for bookingId={}: {}", event.getBookingId(), e.getMessage());
            publishFailure(event.getBookingId(), event.getCustomerId(), "PAYMENT_ERROR", e.getMessage());
        }
    }

    private void publishSuccess(UUID bookingId, UUID customerId, String transactionId) {
        PaymentResultEvent result = new PaymentResultEvent(
                bookingId,
                customerId,
                "SUCCESS",
                transactionId,
                null,
                null);
        paymentEventsProducer.publishPaymentResult(result);
    }

    private void publishFailure(UUID bookingId, UUID customerId, String code, String message) {
        PaymentResultEvent result = new PaymentResultEvent(
                bookingId,
                customerId,
                "FAILED",
                null,
                code,
                message);
        paymentEventsProducer.publishPaymentResult(result);
    }
}

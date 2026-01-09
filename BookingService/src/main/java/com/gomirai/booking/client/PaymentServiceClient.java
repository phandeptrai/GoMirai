package com.gomirai.booking.client;

import com.gomirai.booking.dto.external.RidePaymentRequest;
import com.gomirai.booking.dto.external.TransactionResponse;
import com.gomirai.booking.dto.external.RefundRequest;
import com.gomirai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.observation.annotation.Observed;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Client for calling PaymentService via Consul service discovery
 * Similar pattern to PricingServiceClient and MapServiceClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final DiscoveryClient discoveryClient;
    private final RestTemplate restTemplate;

    @Value("${booking.payment-service.timeout:5000}")
    private int timeoutMs;

    private static final String PAYMENT_SERVICE_NAME = "PaymentService";
    private static final String PAY_RIDE_PATH = "/api/payment/internal/ride";
    private static final String REFUND_PATH = "/api/payment/internal/refund";

    /**
     * Call PaymentService to process ride payment from wallet
     * Throws BusinessException with specific error codes:
     * - INSUFFICIENT_BALANCE: Wallet balance is not enough
     * - PAYMENT_UNAVAILABLE: Service is down or unreachable
     * - PAYMENT_FAILED: Other payment errors
     */
    @Observed(name = "payment.service.payRide")
    @CircuitBreaker(name = "paymentService", fallbackMethod = "payRideFallback")
    public TransactionResponse payRide(RidePaymentRequest request) {
        ServiceInstance instance = getServiceInstance();
        if (instance == null) {
            log.error("PaymentService not found in service discovery");
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service is currently unavailable");
        }

        URI url = URI.create(String.format("http://%s:%d%s",
                instance.getHost(), instance.getPort(), PAY_RIDE_PATH));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward JWT token from current request
        String jwtToken = getCurrentJwtToken();
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
            log.debug("Forwarding JWT token to PaymentService");
        }

        HttpEntity<RidePaymentRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Calling PaymentService to process ride payment: bookingId={}, amount={}",
                    request.getBookingId(), request.getAmount());

            ResponseEntity<TransactionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, TransactionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Payment successful: transactionId={}, newBalance={}",
                        response.getBody().getTransactionId(), response.getBody().getNewBalance());
                return response.getBody();
            }

            throw new BusinessException("PAYMENT_FAILED: Payment service returned invalid response");

        } catch (ResourceAccessException e) {
            log.error("PaymentService timeout or connection error: {}", e.getMessage());
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service timeout or connection failed", e);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PaymentService HTTP client error: {} - Response body: {}", e.getStatusCode(), responseBody);

            // Handle insufficient balance error
            if (e.getStatusCode().value() == 400 && responseBody != null
                    && responseBody.contains("INSUFFICIENT_BALANCE")) {
                throw new BusinessException("INSUFFICIENT_BALANCE: Số dư ví không đủ để thanh toán");
            }

            // Handle wallet not found
            if (e.getStatusCode().value() == 404 && responseBody != null && responseBody.contains("WALLET_NOT_FOUND")) {
                throw new BusinessException("WALLET_NOT_FOUND: Ví chưa được kích hoạt");
            }

            throw new BusinessException("PAYMENT_FAILED: Thanh toán thất bại - " + responseBody);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PaymentService HTTP server error: {} - Response body: {}", e.getStatusCode(), responseBody);
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service error. Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error calling PaymentService", e);
            throw new BusinessException("PAYMENT_FAILED: Failed to process payment", e);
        }
    }

    /**
     * Fallback for payRide
     */
    public TransactionResponse payRideFallback(RidePaymentRequest request, Exception e) {
        log.error("Fallback triggered for PaymentService.payRide. Error: {}", e.getMessage());
        if (e instanceof BusinessException
                && (e.getMessage().contains("INSUFFICIENT_BALANCE") || e.getMessage().contains("WALLET_NOT_FOUND"))) {
            throw (BusinessException) e;
        }
        throw new BusinessException(
                "PAYMENT_SERVICE_DOWN: Hệ thống thanh toán đang bảo trì. Vui lòng thanh toán tiền mặt hoặc thử lại sau.");
    }

    /**
     * Call PaymentService to refund a ride payment
     * Used when customer cancels a booking that was paid with wallet
     */
    @Observed(name = "payment.service.refund")
    @CircuitBreaker(name = "paymentService") // Refund can also have CB
    public TransactionResponse refund(RefundRequest request) {
        ServiceInstance instance = getServiceInstance();
        if (instance == null) {
            log.error("PaymentService not found in service discovery for refund");
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service is currently unavailable");
        }

        URI url = URI.create(String.format("http://%s:%d%s",
                instance.getHost(), instance.getPort(), REFUND_PATH));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward JWT token
        String jwtToken = getCurrentJwtToken();
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
        }

        HttpEntity<RefundRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Calling PaymentService to refund: bookingId={}, amount={}",
                    request.getBookingId(), request.getAmount());

            ResponseEntity<TransactionResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, TransactionResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Refund successful: transactionId={}, newBalance={}",
                        response.getBody().getTransactionId(), response.getBody().getNewBalance());
                return response.getBody();
            }

            throw new BusinessException("REFUND_FAILED: Refund service returned invalid response");

        } catch (ResourceAccessException e) {
            log.error("PaymentService timeout or connection error during refund: {}", e.getMessage());
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service timeout or connection failed", e);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PaymentService HTTP client error during refund: {} - Response body: {}",
                    e.getStatusCode(), responseBody);
            throw new BusinessException("REFUND_FAILED: Refund failed - " + responseBody);
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PaymentService HTTP server error during refund: {} - Response body: {}",
                    e.getStatusCode(), responseBody);
            throw new BusinessException("PAYMENT_UNAVAILABLE: Payment service error during refund");
        } catch (Exception e) {
            log.error("Unexpected error calling PaymentService for refund", e);
            throw new BusinessException("REFUND_FAILED: Failed to process refund", e);
        }
    }

    private ServiceInstance getServiceInstance() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(PAYMENT_SERVICE_NAME);
            if (instances == null || instances.isEmpty()) {
                log.warn("No instances found for service: {}. Available services: {}",
                        PAYMENT_SERVICE_NAME, discoveryClient.getServices());
                return null;
            }
            log.debug("Found {} instance(s) for PaymentService", instances.size());
            return instances.get(0);
        } catch (Exception e) {
            log.error("Error getting service instance for {}", PAYMENT_SERVICE_NAME, e);
            return null;
        }
    }

    /**
     * Get JWT token from current HTTP request context
     * This allows service-to-service calls to forward the customer's authentication
     * token
     */
    private String getCurrentJwtToken() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder
                    .getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7); // Remove "Bearer " prefix
            }

            return null;
        } catch (Exception e) {
            log.debug("Could not extract JWT token from request context: {}", e.getMessage());
            return null;
        }
    }
}

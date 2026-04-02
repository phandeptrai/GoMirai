package com.gomirai.booking.client;

import com.gomirai.booking.dto.external.PricingServiceEstimateRequest;
import com.gomirai.booking.dto.external.PricingServiceResponse;
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
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingServiceClient {

    private final DiscoveryClient discoveryClient;
    @org.springframework.beans.factory.annotation.Qualifier("pricingServiceRestTemplate")
    private final RestTemplate restTemplate;
    private final java.util.concurrent.atomic.AtomicInteger nextInstanceIndex = new java.util.concurrent.atomic.AtomicInteger(
            0);

    @Value("${booking.pricing-service.timeout:5000}")
    private int timeoutMs;

    private static final String PRICING_SERVICE_NAME = "PricingService";
    private static final String PRICING_SERVICE_PATH = "/api/pricing/estimate";

    /**
     * Call Pricing Service synchronously to estimate fare
     * Throws BusinessException with PRICING_UNAVAILABLE code on failure
     */
    @Observed(name = "pricing.service.estimateFare")
    @CircuitBreaker(name = "pricingService", fallbackMethod = "estimateFareFallback")
    @Retry(name = "pricingService")
    public PricingServiceResponse estimateFare(
            String vehicleType,
            double distanceKm,
            int durationMinutes,
            String region) {

        ServiceInstance instance = getServiceInstance();
        if (instance == null) {
            log.error("PricingService not found in service discovery");
            throw new BusinessException("PRICING_UNAVAILABLE: Pricing service is currently unavailable");
        }

        URI url = URI.create(String.format("http://%s:%d%s",
                instance.getHost(), instance.getPort(), PRICING_SERVICE_PATH));

        PricingServiceEstimateRequest request = new PricingServiceEstimateRequest(
                vehicleType, distanceKm, durationMinutes, region);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward JWT token from current request to PricingService
        String jwtToken = getCurrentJwtToken();
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
            log.debug("Forwarding JWT token to PricingService");
        } else {
            log.warn("No JWT token found in current request context");
        }

        HttpEntity<PricingServiceEstimateRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Calling PricingService: {} with vehicleType={}, distanceKm={}, durationMinutes={}, region={}",
                    url, vehicleType, distanceKm, durationMinutes, region);
            ResponseEntity<PricingServiceResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, PricingServiceResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("PricingService response: fare={}, ruleId={}",
                        response.getBody().getEstimatedFare(), response.getBody().getAppliedRuleId());
                return response.getBody();
            }

            throw new BusinessException("PRICING_UNAVAILABLE: Pricing service returned invalid response");

        } catch (ResourceAccessException e) {
            log.error("PricingService timeout or connection error: {}", e.getMessage());
            throw new BusinessException("PRICING_UNAVAILABLE: Pricing service timeout or connection failed", e);
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PricingService HTTP client error: {} - Response body: {}", e.getStatusCode(), responseBody);
            log.error("Request details: vehicleType={}, distanceKm={}, durationMinutes={}, region={}",
                    vehicleType, distanceKm, durationMinutes, region);

            // If it's PRICING_RULE_NOT_FOUND, provide more helpful error message
            if (e.getStatusCode().value() == 404 && responseBody != null
                    && responseBody.contains("PRICING_RULE_NOT_FOUND")) {
                throw new BusinessException("PRICING_RULE_NOT_FOUND: Không tìm thấy quy tắc giá cho loại xe " +
                        vehicleType + " tại khu vực " + region
                        + ". Vui lòng liên hệ quản trị viên để tạo quy tắc giá hoặc thử lại sau.");
            }

            throw new BusinessException(
                    "PRICING_UNAVAILABLE: Pricing service error: " + e.getStatusCode() + ". Please try again later.");
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("PricingService HTTP server error: {} - Response body: {}", e.getStatusCode(), responseBody);
            throw new BusinessException(
                    "PRICING_UNAVAILABLE: Pricing service error: " + e.getStatusCode() + ". Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error calling PricingService", e);
            throw new BusinessException("PRICING_UNAVAILABLE: Failed to estimate fare", e);
        }
    }

    /**
     * Fallback method for estimateFare
     */
    public PricingServiceResponse estimateFareFallback(
            String vehicleType,
            double distanceKm,
            int durationMinutes,
            String region,
            Exception e) {
        log.error("Fallback triggered for PricingService. Error: {}", e.getMessage());

        // If it's a business exception (e.g. Rule Not Found), we should respect it
        if (e instanceof BusinessException && e.getMessage().contains("PRICING_RULE_NOT_FOUND")) {
            throw (BusinessException) e;
        }

        // Graceful degradation: Return a fixed default fare or throw specific error
        // For ride-hailing, we can't easily guess price, but we could return last known
        // good price or default
        // Here we throw a specialized exception for the UI
        throw new BusinessException("PRICING_SERVICE_DOWN: Hệ thống tính giá đang bận. Vui lòng thử lại sau.");
    }

    private ServiceInstance getServiceInstance() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(PRICING_SERVICE_NAME);
            if (instances == null || instances.isEmpty()) {
                log.warn("No instances found for service: {}. Available services: {}",
                        PRICING_SERVICE_NAME, discoveryClient.getServices());
                return null;
            }
            log.debug("Found {} instance(s) for PricingService", instances.size());

            // Round Robin Load Balancing
            int index = nextInstanceIndex.getAndIncrement() % instances.size();
            // Handle negative result from getAndIncrement overflow
            if (index < 0)
                index = 0;

            ServiceInstance selected = instances.get(index);
            log.info("Selected PricingService instance (Round Robin): {}:{} (Index: {})",
                    selected.getHost(), selected.getPort(), index);
            return selected;
        } catch (Exception e) {
            log.error("Error getting service instance for {}", PRICING_SERVICE_NAME, e);
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

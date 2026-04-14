package com.gomirai.ride.booking.client;

import com.gomirai.ride.booking.dto.external.DriverLocationResponse;
import com.gomirai.ride.booking.dto.external.DriverGeoStateResponse;
import com.gomirai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackingServiceClient {

    private final DiscoveryClient discoveryClient;
    @org.springframework.beans.factory.annotation.Qualifier("trackingServiceRestTemplate")
    private final RestTemplate restTemplate;

    @Value("${booking.tracking-service.timeout:5000}")
    private int timeoutMs;

    private static final String TRACKING_SERVICE_NAME = "tracking-service";

    private final java.util.concurrent.atomic.AtomicInteger nextInstanceIndex = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * Get driver's current location from TrackingService
     * INTERNAL call: uses /api/tracking/internal/drivers/{driverId}
     *
     * Rationale:
     * - API Gateway strips Authorization and uses InternalApiKey + delegation headers.
     * - This service-to-service call should not depend on end-user JWT propagation.
     */
    @Cacheable(
            cacheNames = "driver_location",
            cacheManager = "shortLivedCaffeineCacheManager",
            key = "#driverId.toString()",
            unless = "#result == null")
    public DriverGeoStateResponse getDriverLocation(UUID driverId) {
        try {
            ServiceInstance instance = getServiceInstance();
            if (instance == null) {
                log.error("TrackingService instance not found");
                return null;
            }

            String url = instance.getUri() + "/api/tracking/internal/drivers/" + driverId;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<DriverGeoStateResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, DriverGeoStateResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            return null;
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                log.debug("Driver location not found: {}", driverId);
                return null;
            }
            log.error("TrackingService HTTP client error: {} - Response body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (HttpServerErrorException e) {
            log.error("TrackingService HTTP server error: {} - Response body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (ResourceAccessException e) {
            log.error("TrackingService connection timeout or refused", e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error calling TrackingService", e);
            return null;
        }
    }

    /**
     * Find nearby drivers using TrackingService
     */
    public List<DriverLocationResponse> findNearbyDrivers(
            double latitude,
            double longitude,
            double radiusKm,
            String vehicleType) {
        try {
            ServiceInstance instance = getServiceInstance();
            if (instance == null) {
                log.error("TrackingService instance not found");
                return List.of();
            }

            String url = instance.getUri() + "/api/tracking/nearby";

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("latitude", latitude);
            requestBody.put("longitude", longitude);
            requestBody.put("radius", radiusKm * 1000); // Convert km to meters
            requestBody.put("vehicleType", vehicleType);
            requestBody.put("limit", 50); // Get up to 50 nearby drivers

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Service-to-service call via Internal API Key (see RestTemplate interceptor).

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ParameterizedTypeReference<List<DriverLocationResponse>> responseType = new ParameterizedTypeReference<List<DriverLocationResponse>>() {
            };

            ResponseEntity<List<DriverLocationResponse>> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, responseType);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }

            return List.of();
        } catch (Exception e) {
            log.error("Error finding nearby drivers", e);
            return List.of();
        }
    }

    private ServiceInstance getServiceInstance() {
        List<ServiceInstance> instances = discoveryClient.getInstances(TRACKING_SERVICE_NAME);
        if (instances == null || instances.isEmpty()) {
            return null;
        }
        
        // Round Robin Load Balancing
        int index = nextInstanceIndex.getAndIncrement() % instances.size();
        if (index < 0) index = 0;
        
        ServiceInstance selected = instances.get(index);
        log.debug("Selected TrackingService instance (Round Robin): {}:{} (Index: {})",
                 selected.getHost(), selected.getPort(), index);
        return selected;
    }
}

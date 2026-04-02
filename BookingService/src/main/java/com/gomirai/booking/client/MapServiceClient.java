package com.gomirai.booking.client;

import com.gomirai.booking.dto.external.MapServiceRouteRequest;
import com.gomirai.booking.dto.external.MapServiceRouteResponse;
import com.gomirai.booking.dto.external.GeoPoint;
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
public class MapServiceClient {

    private final DiscoveryClient discoveryClient;
    @org.springframework.beans.factory.annotation.Qualifier("mapServiceRestTemplate")
    private final RestTemplate restTemplate;
    private final java.util.concurrent.atomic.AtomicInteger nextInstanceIndex = new java.util.concurrent.atomic.AtomicInteger(
            0);

    @Value("${booking.map-service.timeout:5000}")
    private int timeoutMs;

    private static final String MAP_SERVICE_NAME = "MapService";
    private static final String MAP_SERVICE_PATH = "/api/map/directions";

    /**
     * Call Map Service synchronously to get route information
     * Throws BusinessException with MAP_UNAVAILABLE code on failure
     */
    @Observed(name = "map.service.getRoute")
    @CircuitBreaker(name = "mapService", fallbackMethod = "getRouteFallback")
    @Retry(name = "mapService")
    public MapServiceRouteResponse getRoute(GeoPoint origin, GeoPoint destination) {
        ServiceInstance instance = getServiceInstance();
        if (instance == null) {
            log.error("MapService not found in Consul service discovery. Service name: {}", MAP_SERVICE_NAME);
            try {
                log.error("Available services in Consul: {}", discoveryClient.getServices());
            } catch (Exception e) {
                log.error("Could not get services list from Consul: {}", e.getMessage());
            }
            log.error("Please ensure MapService is running and registered with Consul");
            throw new BusinessException(
                    "MAP_UNAVAILABLE: Map service is currently unavailable. Please try again later.");
        }

        log.info("Found MapService instance: {}:{}", instance.getHost(), instance.getPort());
        URI url = URI.create(String.format("http://%s:%d%s",
                instance.getHost(), instance.getPort(), MAP_SERVICE_PATH));

        MapServiceRouteRequest request = new MapServiceRouteRequest(origin, destination, "driving");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Forward JWT token from current request to MapService
        String jwtToken = getCurrentJwtToken();
        if (jwtToken != null) {
            headers.setBearerAuth(jwtToken);
            log.debug("Forwarding JWT token to MapService");
        } else {
            log.warn("No JWT token found in current request context");
        }

        HttpEntity<MapServiceRouteRequest> entity = new HttpEntity<>(request, headers);

        try {
            log.info("Calling MapService: {} (instance: {}:{})", url, instance.getHost(), instance.getPort());
            ResponseEntity<MapServiceRouteResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, MapServiceRouteResponse.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("MapService response: distance={}m, duration={}s",
                        response.getBody().getDistance(), response.getBody().getDuration());
                return response.getBody();
            }

            log.error("MapService returned invalid response: status={}, body={}",
                    response.getStatusCode(), response.getBody());
            throw new BusinessException("MAP_UNAVAILABLE: Map service returned invalid response");

        } catch (ResourceAccessException e) {
            log.error("MapService timeout or connection error after {}ms: {}", timeoutMs, e.getMessage());
            log.error("Full error details:", e);
            throw new BusinessException(
                    "MAP_UNAVAILABLE: Map service timeout or connection failed. Please try again later.");
        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("MapService HTTP client error: {} - Response body: {}", e.getStatusCode(), responseBody);
            log.error("Request URL: {}, Headers: {}", url, headers);
            throw new BusinessException(
                    "MAP_UNAVAILABLE: Map service error: " + e.getStatusCode() + ". Please try again later.");
        } catch (HttpServerErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("MapService HTTP server error: {} - Response body: {}", e.getStatusCode(), responseBody);
            throw new BusinessException(
                    "MAP_UNAVAILABLE: Map service error: " + e.getStatusCode() + ". Please try again later.");
        } catch (Exception e) {
            log.error("Unexpected error calling MapService: {}", e.getMessage(), e);
            throw new BusinessException("MAP_UNAVAILABLE: Failed to calculate route. Please try again later.");
        }
    }

    /**
     * Fallback method for getRoute when Circuit Breaker is OPEN or calls fail
     */
    public MapServiceRouteResponse getRouteFallback(GeoPoint origin, GeoPoint destination, Exception e) {
        log.error("Fallback triggered for MapService. Error: {}", e.getMessage());
        // For MapService, if it's down, we might want to return a very basic estimate
        // but it's hard to estimate distance without coordinates logic.
        // For now, we throw a more descriptive BusinessException or return a mocked
        // safe response
        if (e instanceof BusinessException) {
            throw (BusinessException) e;
        }
        throw new BusinessException("MAP_SERVICE_DOWN: Hệ thống định vị đang gặp sự cố. Vui lòng thử lại sau.");
    }

    private ServiceInstance getServiceInstance() {
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(MAP_SERVICE_NAME);
            if (instances == null || instances.isEmpty()) {
                log.warn("No instances found for service: {}. Available services: {}",
                        MAP_SERVICE_NAME, discoveryClient.getServices());
                return null;
            }
            log.debug("Found {} instance(s) for MapService", instances.size());

            // Round Robin Load Balancing
            int index = nextInstanceIndex.getAndIncrement() % instances.size();
            // Handle overflow
            if (index < 0)
                index = 0;

            ServiceInstance selected = instances.get(index);
            log.info("Selected MapService instance (Round Robin): {}:{} (Index: {})",
                    selected.getHost(), selected.getPort(), index);
            return selected;
        } catch (Exception e) {
            log.error("Error getting service instance for {}", MAP_SERVICE_NAME, e);
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

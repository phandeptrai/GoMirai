package com.gomirai.booking.client;

import com.gomirai.booking.dto.external.DriverLocationResponse;
import com.gomirai.booking.dto.external.DriverGeoStateResponse;
import com.gomirai.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
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
    private final RestTemplate restTemplate;
    
    @Value("${booking.tracking-service.timeout:5000}")
    private int timeoutMs;
    
    private static final String TRACKING_SERVICE_NAME = "TrackingService";
    
    /**
     * Get driver's current location from TrackingService
     * Uses /api/tracking/me endpoint which allows driver to get their own location
     */
    public DriverGeoStateResponse getDriverLocation(UUID driverId) {
        try {
            ServiceInstance instance = getServiceInstance();
            if (instance == null) {
                log.error("TrackingService instance not found");
                return null;
            }
            
            // Use /me endpoint which allows driver to get their own location
            // Note: This requires the JWT token to be passed in the request
            String url = instance.getUri() + "/api/tracking/me";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            copyAuthHeader(headers);
            
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
            copyAuthHeader(headers);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ParameterizedTypeReference<List<DriverLocationResponse>> responseType = 
                new ParameterizedTypeReference<List<DriverLocationResponse>>() {};
            
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
        return instances.get(0); // Use first available instance
    }
    
    private void copyAuthHeader(HttpHeaders headers) {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null) {
                    headers.set("Authorization", authHeader);
                }
            }
        } catch (Exception e) {
            log.debug("Could not copy auth header", e);
        }
    }
}







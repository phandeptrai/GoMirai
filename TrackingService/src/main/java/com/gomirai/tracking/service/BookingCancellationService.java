package com.gomirai.tracking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service để gọi BookingService hủy booking khi không tìm được tài xế
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCancellationService {
    
    private final RestTemplate restTemplate;
    
    @Value("${booking.service.url:http://BookingService}")
    private String bookingServiceUrl;
    
    /**
     * Gọi BookingService để hủy booking với lý do không tìm được tài xế
     */
    public void cancelBookingNoDriverFound(UUID bookingId) {
        try {
            String url = bookingServiceUrl + "/api/booking/" + bookingId + "/cancel-no-driver";
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("reason", "Không tìm được tài xế trong khu vực");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(url, HttpMethod.POST, request, Void.class);
            
            log.info("Successfully cancelled booking {} - no driver found", bookingId);
        } catch (Exception e) {
            log.error("Error cancelling booking {} - no driver found", bookingId, e);
        }
    }
}






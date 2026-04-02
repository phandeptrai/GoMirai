package com.gomirai.tracking.client;

import com.gomirai.tracking.dto.BookingInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.observation.annotation.Observed;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingServiceClient {

    private final RestTemplate restTemplate;

    @Value("${booking.service.url:http://BookingService}")
    private String bookingServiceUrl;

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    /**
     * Lấy thông tin booking từ BookingService
     * Sử dụng internal endpoint /info không cần authentication
     */
    @Observed(name = "booking.service.getBookingInfo")
    @CircuitBreaker(name = "bookingService", fallbackMethod = "getBookingInfoFallback")
    @Retry(name = "bookingService")
    public BookingInfoResponse getBookingInfo(UUID bookingId) {
        try {
            String url = bookingServiceUrl + "/api/booking/" + bookingId + "/info";
            log.debug("Fetching booking info for bookingId={}", bookingId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", internalApiKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            log.debug("BookingService response status={} for bookingId={}", response.getStatusCode(), bookingId);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                // ApiResponse structure: { success: true, message: "...", data: {...} }
                Map<String, Object> data = (Map<String, Object>) body.get("data");

                if (data == null) {
                    // Maybe response is direct BookingResponse, not wrapped
                    data = body;
                    log.trace("No 'data' wrapper for bookingId={}", bookingId);
                }

                if (data != null) {
                    BookingInfoResponse info = new BookingInfoResponse();

                    // Extract dropoff location (AddressSnapshot structure)
                    Map<String, Object> dropoffLocation = (Map<String, Object>) data.get("dropoffLocation");
                    if (dropoffLocation != null) {
                        Object lat = dropoffLocation.get("latitude");
                        Object lng = dropoffLocation.get("longitude");
                        if (lat != null) {
                            info.setDropoffLatitude(((Number) lat).doubleValue());
                        }
                        if (lng != null) {
                            info.setDropoffLongitude(((Number) lng).doubleValue());
                        }
                        // Try both fullAddress and address fields
                        String dropoffAddr = (String) (dropoffLocation.get("fullAddress") != null
                                ? dropoffLocation.get("fullAddress")
                                : dropoffLocation.get("address"));
                        info.setDropoffAddress(dropoffAddr);
                    }

                    // Extract pickup address (AddressSnapshot structure)
                    Map<String, Object> pickupLocation = (Map<String, Object>) data.get("pickupLocation");
                    if (pickupLocation != null) {
                        // Try both fullAddress and address fields
                        String pickupAddr = (String) (pickupLocation.get("fullAddress") != null
                                ? pickupLocation.get("fullAddress")
                                : pickupLocation.get("address"));
                        info.setPickupAddress(pickupAddr);
                    }

                    // Extract price info (BookingPriceSnapshot structure)
                    Map<String, Object> price = (Map<String, Object>) data.get("price");
                    if (price != null) {
                        // Try finalAmount first, then totalAmount, then estimatedTotal
                        Object finalAmount = price.get("finalAmount");
                        if (finalAmount == null) {
                            finalAmount = price.get("totalAmount");
                        }
                        if (finalAmount == null) {
                            finalAmount = price.get("estimatedTotal");
                        }
                        if (finalAmount != null) {
                            info.setEstimatedFare(((Number) finalAmount).doubleValue());
                        }
                        info.setCurrency((String) (price.get("currency") != null
                                ? price.get("currency")
                                : "VND"));
                    }

                    // Extract distance and duration
                    Object distance = data.get("estimatedDistanceKm");
                    Object duration = data.get("estimatedDurationMinutes");
                    if (distance != null) {
                        info.setEstimatedDistanceKm(((Number) distance).doubleValue());
                    }
                    if (duration != null) {
                        info.setEstimatedDurationMinutes(((Number) duration).intValue());
                    }

                    log.debug("Extracted booking info for bookingId={}", bookingId);
                    return info;
                } else {
                    log.warn("Booking data is null for bookingId={}", bookingId);
                }
            } else {
                log.warn("Failed to get booking info: bookingId={}, status={}", bookingId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error fetching booking info for bookingId={}", bookingId, e);
        }

        return null;
    }

    /**
     * Fallback for getBookingInfo
     */
    public BookingInfoResponse getBookingInfoFallback(UUID bookingId, Exception e) {
        log.error("Fallback triggered for BookingService.getBookingInfo (bookingId={}). Error: {}", bookingId,
                e.getMessage());
        // For search drivers, if booking info is missing, we can't search correctly.
        // Return null or throw specific error.
        return null;
    }

    /**
     * Check if booking is still in PENDING status
     * Returns true if booking is still PENDING, false otherwise (MATCHED, CANCELED,
     * etc.)
     */
    @Observed(name = "booking.service.isBookingStillPending")
    @CircuitBreaker(name = "bookingService", fallbackMethod = "isBookingStillPendingFallback")
    @Retry(name = "bookingService")
    public boolean isBookingStillPending(UUID bookingId) {
        try {
            String url = bookingServiceUrl + "/api/booking/" + bookingId + "/info";
            log.debug("Checking booking status from: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Api-Key", internalApiKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Map<String, Object> data = (Map<String, Object>) body.get("data");

                if (data == null) {
                    data = body; // Direct response without wrapper
                }

                if (data != null) {
                    String status = (String) data.get("status");
                    log.debug("Booking {} status: {}", bookingId, status);
                    return "PENDING".equals(status);
                }
            }
        } catch (Exception e) {
            log.error("Error checking booking status for bookingId={}", bookingId, e);
        }

        return false; // If error or not found, assume not pending
    }

    /**
     * Fallback for isBookingStillPending
     */
    public boolean isBookingStillPendingFallback(UUID bookingId, Exception e) {
        log.error("Fallback triggered for BookingService.isBookingStillPending (bookingId={}). Error: {}", bookingId,
                e.getMessage());
        // If BookingService is down, we should stop searching to prevent wasting
        // resources
        return false;
    }
}

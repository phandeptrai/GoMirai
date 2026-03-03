package com.gomirai.map.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.map.dto.GeocodeResponse;
import com.gomirai.map.dto.RouteRequest;
import com.gomirai.map.dto.RouteResponse;
import com.gomirai.map.dto.GeoPoint;

@ExtendWith(MockitoExtension.class)
class MapboxServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private MapboxService mapboxService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // Inject private values using ReflectionTestUtils
        ReflectionTestUtils.setField(mapboxService, "accessToken", "test-token");
        ReflectionTestUtils.setField(mapboxService, "baseUrl", "https://api.mapbox.com");
        ReflectionTestUtils.setField(mapboxService, "objectMapper", objectMapper);
    }

    @Test
    @DisplayName("1. Get Directions - Success")
    void getDirections_Success() {
        // Arrange
        RouteRequest request = new RouteRequest();
        request.setProfile("driving");
        request.setOrigin(new GeoPoint(10.762622, 106.660172));
        request.setDestination(new GeoPoint(10.772622, 106.670172));

        String mockResponse = "{" +
                "  \"routes\": [" +
                "    {" +
                "      \"legs\": [{\"distance\": 1200.5, \"duration\": 300, \"steps\": []}]," +
                "      \"geometry\": {\"coordinates\": [[106.66, 10.76], [106.67, 10.77]]}" +
                "    }" +
                "  ]" +
                "}";

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        RouteResponse response = mapboxService.getDirections(request);

        // Assert
        assertEquals(1200.5, response.getDistance());
        assertEquals(300, response.getDuration());
        assertEquals(2, response.getGeometry().size());
    }

    @Test
    @DisplayName("2. Geocode - Success")
    void geocode_Success() {
        // Arrange
        String address = "227 Nguyen Van Cu";
        String mockResponse = "{" +
                "  \"features\": [" +
                "    {" +
                "      \"center\": [106.66, 10.76]," +
                "      \"place_name\": \"227 Nguyễn Văn Cừ, Quận 5\"" +
                "    }" +
                "  ]" +
                "}";

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        GeocodeResponse response = mapboxService.geocode(address);

        // Assert
        assertEquals(10.76, response.getLocation().getLatitude());
        assertEquals(106.66, response.getLocation().getLongitude());
        assertTrue(response.getFormattedAddress().contains("Nguyễn Văn Cừ"));
    }
}

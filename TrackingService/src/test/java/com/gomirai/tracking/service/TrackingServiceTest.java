package com.gomirai.tracking.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomirai.common.enums.DriverAvailabilityStatus;
import com.gomirai.common.enums.VehicleType;
import com.gomirai.tracking.dto.DriverLocationResponse;
import com.gomirai.tracking.dto.NearbyDriverRequest;
import com.gomirai.tracking.model.DriverGeoState;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    private GeoOperations<String, String> geoOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TrackingService trackingService;

    private String driverId;
    private DriverGeoState state;

    @BeforeEach
    void setUp() {
        driverId = UUID.randomUUID().toString();
        state = DriverGeoState.builder()
                .driverId(driverId)
                .latitude(10.762622)
                .longitude(106.660172)
                .status(DriverAvailabilityStatus.ONLINE)
                .vehicleType(VehicleType.CAR_4)
                .lastUpdatedAt(System.currentTimeMillis())
                .build();
    }

    @Test
    @DisplayName("1. Update Location - Success")
    void updateLocation_Success() throws Exception {
        // Arrange
        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        trackingService.updateLocation(state);

        // Assert
        verify(geoOperations).add(eq("drivers:geo"), any(), eq(driverId));
        verify(valueOperations).set(eq("drivers:state:" + driverId), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("2. Find Nearby Drivers - Success")
    void findNearbyDrivers_Success() throws Exception {
        // Arrange
        NearbyDriverRequest request = new NearbyDriverRequest();
        request.setLatitude(10.762622);
        request.setLongitude(106.660172);
        request.setRadius(2000.0);
        request.setLimit(10);

        when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Mock Geo results
        RedisGeoCommands.GeoLocation<String> geoLocation = new RedisGeoCommands.GeoLocation<>(driverId,
                new org.springframework.data.geo.Point(106.660172, 10.762622));
        GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult = new GeoResult<>(geoLocation,
                new org.springframework.data.geo.Distance(100.0));
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = new GeoResults<>(List.of(geoResult));

        when(geoOperations.radius(eq("drivers:geo"), any(Circle.class),
                any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        // Mock metadata
        when(valueOperations.get("drivers:state:" + driverId)).thenReturn("{\"driverId\":\"" + driverId + "\"}");
        when(objectMapper.readValue(anyString(), eq(DriverGeoState.class))).thenReturn(state);

        // Act
        List<DriverLocationResponse> results = trackingService.findNearbyDrivers(request);

        // Assert
        assertFalse(results.isEmpty());
        assertEquals(driverId, results.get(0).getDriverId());
    }
}

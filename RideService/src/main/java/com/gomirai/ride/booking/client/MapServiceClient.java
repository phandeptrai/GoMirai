package com.gomirai.ride.booking.client;

import com.gomirai.ride.booking.dto.external.MapServiceRouteResponse;
import com.gomirai.ride.booking.dto.external.GeoPoint;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.ride.map.service.MapboxService;
import com.gomirai.ride.map.dto.RouteRequest;
import com.gomirai.ride.map.dto.RouteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MapServiceClient {

    private final MapboxService internalMapboxService;

    @Cacheable(
            cacheNames = "map_route",
            key = "T(com.gomirai.ride.booking.client.cache.CacheKeyUtils).routeKey(#origin, #destination, 'driving')")
    public MapServiceRouteResponse getRoute(GeoPoint origin, GeoPoint destination) {
        try {
            RouteRequest request = new RouteRequest(
                new com.gomirai.ride.map.dto.GeoPoint(origin.getLatitude(), origin.getLongitude()),
                new com.gomirai.ride.map.dto.GeoPoint(destination.getLatitude(), destination.getLongitude()),
                "driving"
            );

            RouteResponse resp = internalMapboxService.getDirections(request);
            
            MapServiceRouteResponse clientResp = new MapServiceRouteResponse();
            clientResp.setDistance(resp.getDistance());
            clientResp.setDuration(resp.getDuration());
            return clientResp;
            
        } catch (BusinessException e) {
            log.error("Internal MapboxService error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling internal MapboxService", e);
            throw new BusinessException("MAP_UNAVAILABLE: Failed to calculate route", e);
        }
    }
}

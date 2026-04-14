package com.gomirai.ride.booking.client;

import com.gomirai.ride.booking.dto.external.PricingServiceResponse;
import com.gomirai.common.exception.BusinessException;
import com.gomirai.ride.pricing.service.PricingService;
import com.gomirai.ride.pricing.dto.request.EstimateRequest;
import com.gomirai.ride.pricing.dto.response.PricingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PricingServiceClient {

    private final PricingService internalPricingService;

    @Cacheable(
            cacheNames = "pricing_estimate",
            key = "T(com.gomirai.ride.booking.client.cache.CacheKeyUtils).pricingKey(#vehicleType, #distanceKm, #durationMinutes, #region)")
    public PricingServiceResponse estimateFare(
            String vehicleType,
            double distanceKm,
            int durationMinutes,
            String region) {
        try {
            EstimateRequest req = new EstimateRequest();
            req.setVehicleType(vehicleType);
            req.setDistanceKm(distanceKm);
            req.setDurationMinute(durationMinutes);
            req.setRegion(region);

            PricingResponse resp = internalPricingService.estimate(req);
            
            PricingServiceResponse clientResp = new PricingServiceResponse();
            clientResp.setEstimatedFare(resp.getEstimatedFare());
            clientResp.setAppliedRuleId(resp.getAppliedRuleId());
            return clientResp;
            
        } catch (BusinessException e) {
            log.error("Internal PricingService error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error calling internal PricingService", e);
            throw new BusinessException("PRICING_UNAVAILABLE: Failed to estimate fare", e);
        }
    }
}

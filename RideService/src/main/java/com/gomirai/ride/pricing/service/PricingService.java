package com.gomirai.ride.pricing.service;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.ride.pricing.dto.request.*;
import com.gomirai.ride.pricing.dto.response.PricingResponse;
import com.gomirai.ride.pricing.exception.PricingErrorCode;
import com.gomirai.ride.pricing.model.PricingRule;
import com.gomirai.ride.pricing.cache.PricingRuleCacheService;
import com.gomirai.ride.pricing.repository.PricingRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PricingService {

    private final PricingRuleRepository repository;
    private final PricingRuleCacheService ruleCache;

    public PricingService(PricingRuleRepository repository, PricingRuleCacheService ruleCache) {
        this.repository = repository;
        this.ruleCache = ruleCache;
    }

    public PricingResponse estimate(EstimateRequest req) {
        validateEstimate(req);
        PricingRule rule = findActiveRule(req.getVehicleType(), req.getRegion());
        long fare = calculateFare(req.getDistanceKm(), req.getDurationMinute(), rule);
        return new PricingResponse(fare, rule.getRuleId());
    }

    public PricingResponse calculateFinal(FinalCalculationRequest req,
            double estimatedDistanceKm,
            int estimatedDurationMinute) {
        validateFinal(req);

        if (req.getActualDistanceKm() < estimatedDistanceKm * 0.5) {
            throw new BusinessException(PricingErrorCode.POSSIBLE_CHEAT_DISTANCE);
        }
        if (req.getActualDurationMinute() < estimatedDurationMinute * 0.5) {
            throw new BusinessException(PricingErrorCode.POSSIBLE_CHEAT_DURATION);
        }

        PricingRule rule = findActiveRule(req.getVehicleType(), req.getRegion());
        long fare = calculateFare(req.getActualDistanceKm(), req.getActualDurationMinute(), rule);
        return new PricingResponse(fare, rule.getRuleId());
    }

    private PricingRule findActiveRule(String vehicleType, String region) {
        PricingRule rule = ruleCache.findActiveRule(vehicleType, region);
        if (rule == null) {
            throw new BusinessException(PricingErrorCode.PRICING_RULE_NOT_FOUND);
        }
        return rule;
    }

    private long calculateFare(double km, int min, PricingRule r) {
        double total = r.getBaseFare() +
                km * r.getPerKmRate() +
                min * r.getPerMinuteRate();
        return Math.round(total * r.getSurgeMultiplier());
    }

    private void validateEstimate(EstimateRequest r) {
        if (r.getVehicleType() == null || r.getVehicleType().isBlank())
            throw new BusinessException(PricingErrorCode.INVALID_VEHICLE_TYPE);
        if (r.getDistanceKm() <= 0)
            throw new BusinessException(PricingErrorCode.INVALID_DISTANCE);
        if (r.getDurationMinute() <= 0)
            throw new BusinessException(PricingErrorCode.INVALID_DURATION);
        if (r.getRegion() == null || r.getRegion().isBlank())
            throw new BusinessException(PricingErrorCode.INVALID_REGION);
    }

    private void validateFinal(FinalCalculationRequest r) {
        if (r.getRideId() == null || r.getRideId().isBlank())
            throw new BusinessException(PricingErrorCode.INVALID_RIDE_ID);

        EstimateRequest tmp = new EstimateRequest();
        tmp.setVehicleType(r.getVehicleType());
        tmp.setDistanceKm(r.getActualDistanceKm());
        tmp.setDurationMinute(r.getActualDurationMinute());
        tmp.setRegion(r.getRegion());
        validateEstimate(tmp);
    }

    // ADMIN METHODS
    public PricingRule createOrUpdate(PricingRule rule) {
        if (rule.getSurgeMultiplier() < 1.0)
            throw new BusinessException("surgeMultiplier must be >= 1.0");
        if (rule.getBaseFare() <= 0 || rule.getPerKmRate() <= 0 || rule.getPerMinuteRate() <= 0)
            throw new BusinessException("Rates must be positive");
        PricingRule saved = repository.save(rule);
        if (saved.getVehicleType() != null && saved.getRegion() != null) {
            ruleCache.invalidate(saved.getVehicleType(), saved.getRegion());
        }
        return saved;
    }

    public List<PricingRule> findAll() {
        return repository.findAll();
    }
}
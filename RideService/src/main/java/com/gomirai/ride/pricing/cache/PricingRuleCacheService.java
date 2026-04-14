package com.gomirai.ride.pricing.cache;

import com.gomirai.ride.pricing.model.PricingRule;
import com.gomirai.ride.pricing.repository.PricingRuleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * L1 cache: vehicleType + region → active pricing rule.
 * Now using Spring Cache + Redis abstractly per requirement.
 */
@Service
public class PricingRuleCacheService {

    private final PricingRuleRepository repository;

    public PricingRuleCacheService(PricingRuleRepository repository) {
        this.repository = repository;
    }

    /**
     * Note: Spring Cache unwraps {@code Optional} return values; {@code Optional.empty()}
     * is treated as {@code null} for caching, which RedisCache rejects by default.
     * We return nullable {@link PricingRule} and skip caching when not found.
     */
    @Cacheable(
            value = "pricingRules",
            key = "#vehicleType.trim().toUpperCase() + '|' + #region.trim().toUpperCase()",
            unless = "#result == null"
    )
    public PricingRule findActiveRule(String vehicleType, String region) {
        return repository
                .findFirstByVehicleTypeAndRegionAndActiveTrueOrderBySurgeMultiplierDesc(vehicleType, region)
                .orElse(null);
    }

    @CacheEvict(value = "pricingRules", key = "#vehicleType.trim().toUpperCase() + '|' + #region.trim().toUpperCase()")
    public void invalidate(String vehicleType, String region) {
        // Spring Cache will handle the eviction based on the key
    }

    @CacheEvict(value = "pricingRules", allEntries = true)
    public void invalidateAll() {
        // Clear all cached pricing rules
    }
}

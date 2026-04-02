package com.gomirai.pricing.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.gomirai.pricing.model.PricingRule;
import com.gomirai.pricing.repository.PricingRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * L1 cache: vehicleType + region → active pricing rule. Reduces Mongo reads on hot estimate path.
 */
@Service
public class PricingRuleCacheService {

    private final PricingRuleRepository repository;
    private final Cache<String, Optional<PricingRule>> cache;

    public PricingRuleCacheService(
            PricingRuleRepository repository,
            @Value("${pricing.cache.rule-ttl-seconds:120}") long ttlSeconds,
            @Value("${pricing.cache.max-rules:2000}") long maxEntries) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(Duration.ofSeconds(Math.max(60, Math.min(ttlSeconds, 300))))
                .build();
    }

    private static String key(String vehicleType, String region) {
        return vehicleType.trim().toUpperCase() + "|" + region.trim().toUpperCase();
    }

    public Optional<PricingRule> findActiveRule(String vehicleType, String region) {
        String k = key(vehicleType, region);
        return cache.get(k, kk -> repository
                .findFirstByVehicleTypeAndRegionAndActiveTrueOrderBySurgeMultiplierDesc(vehicleType, region));
    }

    public void invalidate(String vehicleType, String region) {
        cache.invalidate(key(vehicleType, region));
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}

package com.gomirai.ride.pricing.repository;

import com.gomirai.ride.pricing.model.PricingRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends MongoRepository<PricingRule, UUID> {
    Optional<PricingRule> findFirstByVehicleTypeAndRegionAndActiveTrueOrderBySurgeMultiplierDesc(
            String vehicleType, String region);
}
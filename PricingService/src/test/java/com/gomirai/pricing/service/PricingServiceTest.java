package com.gomirai.pricing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.gomirai.common.exception.BusinessException;
import com.gomirai.pricing.dto.request.EstimateRequest;
import com.gomirai.pricing.dto.request.FinalCalculationRequest;
import com.gomirai.pricing.dto.response.PricingResponse;
import com.gomirai.pricing.model.PricingRule;
import com.gomirai.pricing.repository.PricingRuleRepository;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRuleRepository repository;

    @InjectMocks
    private PricingService pricingService;

    private PricingRule mockRule;

    @BeforeEach
    void setUp() {
        mockRule = new PricingRule();
        mockRule.setRuleId(UUID.randomUUID());
        mockRule.setVehicleType("CAR_4");
        mockRule.setRegion("HCM");
        mockRule.setBaseFare(10000.0);
        mockRule.setPerKmRate(5000.0);
        mockRule.setPerMinuteRate(1000.0);
        mockRule.setSurgeMultiplier(1.2); // 20% surge
        mockRule.setActive(true);
    }

    @Test
    @DisplayName("1. Estimate Fare - Success")
    void estimate_Success() {
        // Arrange
        EstimateRequest req = new EstimateRequest();
        req.setVehicleType("CAR_4");
        req.setRegion("HCM");
        req.setDistanceKm(5.0);
        req.setDurationMinute(10);

        when(repository.findFirstByVehicleTypeAndRegionAndActiveTrueOrderBySurgeMultiplierDesc(anyString(),
                anyString()))
                .thenReturn(Optional.of(mockRule));

        // Act
        PricingResponse response = pricingService.estimate(req);

        // Assert
        // Calculation: (10000 + 5*5000 + 10*1000) * 1.2 = (10000 + 25000 + 10000) * 1.2
        // = 45000 * 1.2 = 54000
        assertEquals(54000, response.getEstimatedFare());
        assertEquals(mockRule.getRuleId(), response.getAppliedRuleId());
    }

    @Test
    @DisplayName("2. Estimate Fare - Rule Not Found")
    void estimate_RuleNotFound_ThrowsException() {
        // Arrange
        EstimateRequest req = new EstimateRequest();
        req.setVehicleType("BIKE");
        req.setRegion("LONDON");
        req.setDistanceKm(5.0);
        req.setDurationMinute(10);

        when(repository.findFirstByVehicleTypeAndRegionAndActiveTrueOrderBySurgeMultiplierDesc(anyString(),
                anyString()))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(BusinessException.class, () -> pricingService.estimate(req));
    }

    @Test
    @DisplayName("3. Final Calculation - Anti-Cheat Distance")
    void calculateFinal_CheatDistance_ThrowsException() {
        // Arrange
        FinalCalculationRequest req = new FinalCalculationRequest();
        req.setRideId(UUID.randomUUID().toString());
        req.setVehicleType("CAR_4");
        req.setRegion("HCM");
        req.setActualDistanceKm(2.0); // Estimated was 5.0 -> 2.0 < 5.0 * 0.5 (2.5) -> Cheat!
        req.setActualDurationMinute(10);

        // Act & Assert
        assertThrows(BusinessException.class, () -> pricingService.calculateFinal(req, 5.0, 10));
    }

    @Test
    @DisplayName("4. Create Rule - Invalid Surge")
    void createOrUpdate_InvalidSurge_ThrowsException() {
        // Arrange
        mockRule.setSurgeMultiplier(0.5); // Invalid, must be >= 1.0

        // Act & Assert
        assertThrows(BusinessException.class, () -> pricingService.createOrUpdate(mockRule));
    }
}

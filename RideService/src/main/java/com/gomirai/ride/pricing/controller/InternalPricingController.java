package com.gomirai.ride.pricing.controller;

import com.gomirai.ride.pricing.dto.request.FinalCalculationRequest;
import com.gomirai.ride.pricing.dto.response.PricingResponse;
import com.gomirai.ride.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
public class InternalPricingController {

    private final PricingService service;

    public InternalPricingController(PricingService service) {
        this.service = service;
    }

    @PostMapping("/calculate-final")
    @PreAuthorize("hasAuthority('ROLE_BOOKING_SERVICE')")
    public PricingResponse calculateFinal(
            @Valid @RequestBody FinalCalculationRequest request,
            @RequestParam double estimatedDistanceKm,
            @RequestParam int estimatedDurationMinute) {
        return service.calculateFinal(request, estimatedDistanceKm, estimatedDurationMinute);
    }
}
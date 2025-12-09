package com.gomirai.pricing.controller;

import com.gomirai.pricing.dto.request.FinalCalculationRequest;
import com.gomirai.pricing.dto.response.PricingResponse;
import com.gomirai.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasAuthority('ROLE_BOOKING_SERVICE')")
public class InternalPricingController {

    private final PricingService service;

    public InternalPricingController(PricingService service) {
        this.service = service;
    }

    @PostMapping("/calculate-final")
    public PricingResponse calculateFinal(
            @Valid @RequestBody FinalCalculationRequest request,
            @RequestParam double estimatedDistanceKm,
            @RequestParam int estimatedDurationMinute) {
        return service.calculateFinal(request, estimatedDistanceKm, estimatedDurationMinute);
    }
}
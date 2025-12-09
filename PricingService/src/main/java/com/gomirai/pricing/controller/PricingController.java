package com.gomirai.pricing.controller;

import com.gomirai.pricing.dto.request.EstimateRequest;
import com.gomirai.pricing.dto.response.PricingResponse;
import com.gomirai.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pricing")
@PreAuthorize("hasAnyAuthority('ROLE_CUSTOMER', 'ROLE_DRIVER')")
public class PricingController {

    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    @PostMapping("/estimate")
    public PricingResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return service.estimate(request);
    }
}
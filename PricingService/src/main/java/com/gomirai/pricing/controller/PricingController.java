package com.gomirai.pricing.controller;

import com.gomirai.pricing.dto.request.EstimateRequest;
import com.gomirai.pricing.dto.response.PricingResponse;
import com.gomirai.pricing.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller tính toán giá cước cho chuyến đi.
 * 
 * Chức năng: Ước tính giá dựa trên loại xe, khoảng cách, thời gian, khu vực.
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    @PostMapping("/estimate")
    public PricingResponse estimate(@Valid @RequestBody EstimateRequest request) {
        return service.estimate(request);
    }

    // Debug endpoint - remove after testing
    @GetMapping("/debug/auth")
    public Map<String, Object> debugAuth() {
        Map<String, Object> debug = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            debug.put("authenticated", auth.isAuthenticated());
            debug.put("principal", auth.getPrincipal());
            debug.put("authorities", auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        } else {
            debug.put("authenticated", false);
            debug.put("message", "No authentication found");
        }

        return debug;
    }
}
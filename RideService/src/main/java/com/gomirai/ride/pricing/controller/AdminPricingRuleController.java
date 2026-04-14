package com.gomirai.ride.pricing.controller;

import com.gomirai.ride.pricing.model.PricingRule;
import com.gomirai.ride.pricing.service.PricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pricing/rules")
public class AdminPricingRuleController {

    private final PricingService service;

    public AdminPricingRuleController(PricingService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRule create(@RequestBody PricingRule rule) {
        // ⚠️ BƯỚC KHẮC PHỤC LỖI 500: Gán UUID nếu Rule là mới (ruleId == null)
        if (rule.getRuleId() == null) {
            rule.setRuleId(UUID.randomUUID());
        }
        // Ghi log (tùy chọn, để xác nhận ID đã được gán)
        // log.info("Creating new rule with ID: {}", rule.getRuleId());

        return service.createOrUpdate(rule);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRule update(@PathVariable UUID id, @RequestBody PricingRule rule) {
        rule.setRuleId(id);
        return service.createOrUpdate(rule);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PricingRule> list() {
        return service.findAll();
    }
}
package com.gomirai.pricing.controller;

import com.gomirai.pricing.model.PricingRule;
import com.gomirai.pricing.service.PricingService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pricing/rules")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminPricingRuleController {

    private final PricingService service;

    public AdminPricingRuleController(PricingService service) {
        this.service = service;
    }

    @PostMapping
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
    public PricingRule update(@PathVariable UUID id, @RequestBody PricingRule rule) {
        rule.setRuleId(id);
        return service.createOrUpdate(rule);
    }

    @GetMapping
    public List<PricingRule> list() {
        return service.findAll();
    }
}
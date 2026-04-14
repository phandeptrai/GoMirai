package com.gomirai.driver.client;

import com.gomirai.driver.dto.response.RatingSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "communication-service")
public interface ReviewServiceClient {

    @GetMapping("/api/review/reviewee/{revieweeId}/rating")
    RatingSummaryResponse getRatingSummary(@PathVariable("revieweeId") UUID revieweeId);
}

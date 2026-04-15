package com.gomirai.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reactive Route Configuration (Zero-Copy Enabled).
 * 
 * Replaces legacy ProxyController.
 * Uses Netty-based streaming to proxy requests to downstream services.
 */
@Configuration
public class GatewayRoutesConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayRoutesConfig.class);

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        logger.info("Initializing Custom Reactive Route Locator...");
        return builder.routes()
                // IDENTITY SERVICE
                .route("identity-service", r -> r.path("/api/auth/**", "/api/users/**")
                        .uri("lb://identity-service"))
                
                // RIDE SERVICE (Booking, Pricing, Map)
                .route("ride-service", r -> r.path("/api/booking/**", "/api/pricing/**", "/api/map/**")
                        .uri("lb://ride-service"))
                
                // DRIVER SERVICE
                .route("driver-service", r -> r.path("/api/driver/**", "/api/drivers/**")
                        .filters(f -> f.rewritePath("/api/drivers(?<segment>/?.*)", "/api/driver${segment}"))
                        .uri("lb://driver-service"))
                
                // COMMUNICATION SERVICE (Review, Notification)
                .route("communication-service", r -> r.path("/api/review/**", "/api/notification/**")
                        .uri("lb://communication-service"))
                
                // TRACKING SERVICE
                .route("tracking-service", r -> r.path("/api/tracking/**")
                        .uri("lb://tracking-service"))
                
                .build();
    }
}

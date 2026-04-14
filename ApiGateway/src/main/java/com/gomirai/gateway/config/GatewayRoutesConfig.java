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
                
                // RIDE SERVICE (Booking, Pricing, Review)
                .route("ride-service-booking", r -> r.path("/api/booking/**")
                        .uri("lb://ride-service"))
                .route("ride-service-pricing", r -> r.path("/api/pricing/**")
                        .uri("lb://ride-service"))
                .route("communication-service-review", r -> r.path("/api/review/**")
                        .uri("lb://communication-service"))
                .route("communication-service-notification", r -> r.path("/api/notification/**")
                        .uri("lb://communication-service"))
                
                // TRACKING SERVICE
                .route("tracking-service", r -> r.path("/api/tracking/**", "/api/driver/**")
                        .uri("lb://tracking-service"))
                
                // MAP SERVICE (Optional internal proxy)
                .route("map-service", r -> r.path("/api/map/**")
                        .uri("lb://map-service"))
                
                .build();
    }
}

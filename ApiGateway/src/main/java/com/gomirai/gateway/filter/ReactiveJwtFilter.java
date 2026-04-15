package com.gomirai.gateway.filter;

import com.gomirai.common.security.JwtService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * High-Performance Reactive JWT Filter (Streaming Ready).
 * 
 * Validates JWT without blocking the Netty event loop.
 * Forwards User-Id and Role as headers to downstream services.
 */
@Component
public class ReactiveJwtFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveJwtFilter.class);
    private final JwtService jwtService;

    public ReactiveJwtFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Skip public endpoints
        boolean isPublic = isPublicPath(path);
        if (isPublic) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            logger.warn("Request blocked (Missing JWT): {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);
        Optional<Claims> claimsOpt = jwtService.parseToken(token);

        if (claimsOpt.isEmpty()) {
            logger.warn("Invalid JWT for path: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Claims claims = claimsOpt.get();
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        // ZERO-COPY FORWARDING: Mutate request headers instead of copying body
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header("X-Gateway-User-Id", userId)
                .header("X-Gateway-User-Role", role)
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return path.startsWith("/api/auth/") || 
               path.startsWith("/health") || 
               path.startsWith("/actuator/") ||
               path.startsWith("/api/review/reviewee/") ||
               path.contains("/exists") ||
               path.startsWith("/api/pricing/estimate") ||
               ( (path.startsWith("/api/driver") || path.startsWith("/api/drivers")) && path.contains("/public") );
    }

    @Override
    public int getOrder() {
        return -100; // Run before routing
    }
}

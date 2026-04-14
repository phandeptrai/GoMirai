package com.gomirai.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * High-Performance Reactive Internal API Key Filter.
 * 
 * Secures internal-only endpoints using a shared key.
 * Part of the Zero-Copy reactive pipeline.
 */
@Component
public class ReactiveInternalApiKeyFilter implements GlobalFilter, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveInternalApiKeyFilter.class);
    
    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    
    private final String internalApiKey;

    public ReactiveInternalApiKeyFilter(@Value("${security.internal.api-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // Only enforce for internal-looking paths or specific endpoints if needed
        // For now, if the header is present, we validate it.
        // Downstream services like Tracking will check it again.
        
        String requestKey = exchange.getRequest().getHeaders().getFirst(INTERNAL_API_KEY_HEADER);
        
        if (StringUtils.hasText(requestKey)) {
            if (!internalApiKey.equals(requestKey)) {
                logger.warn("Invalid Internal API Key from {}", exchange.getRequest().getRemoteAddress());
                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -110; // Run before JWT filter
    }
}

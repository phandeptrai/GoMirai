package com.gomirai.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * High-Performance Reactive SLA Filter (Zero-Copy Compatible).
 * 
 * Monitors request processing time across the reactive pipeline.
 * Flags requests exceeding SLA thresholds without blocking throughput.
 */
@Component
public class SlaWallClockFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SlaWallClockFilter.class);

    private static final long REST_WARN_MS = 8_000;
    private static final long REST_HARD_MS = 9_900;
    private static final long LEGACY_TAIL_MS = 29_500;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long t0 = System.currentTimeMillis();
        
        return chain.filter(exchange)
                .doOnError(ex -> log.error("GATEWAY_PIPELINE_ERROR method={} uri={} error={}", 
                        exchange.getRequest().getMethod(), exchange.getRequest().getURI().getPath(), ex.getMessage()))
                .doFinally(signalType -> {
                    long ms = System.currentTimeMillis() - t0;
                    String uri = exchange.getRequest().getURI().getPath();
                    String method = exchange.getRequest().getMethod().name();
                    var statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 0;

                    if (ms >= LEGACY_TAIL_MS) {
                        log.error("SLA_LEGACY_TAIL_30S_CLASS method={} uri={} ms={} status={}", method, uri, ms, status);
                    } else if (ms >= REST_HARD_MS) {
                        log.error("SLA_REST_HARD_CAP_EXCEEDED method={} uri={} ms={} status={}", method, uri, ms, status);
                    } else if (ms >= REST_WARN_MS) {
                        log.warn("SLA_REST_APPROACHING_CAP method={} uri={} ms={} status={}", method, uri, ms, status);
                    }
                    
                    // Always log at debug level to see every transaction
                    log.debug("REQUEST_COMPLETED method={} uri={} ms={} status={} signal={}", method, uri, ms, status, signalType);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

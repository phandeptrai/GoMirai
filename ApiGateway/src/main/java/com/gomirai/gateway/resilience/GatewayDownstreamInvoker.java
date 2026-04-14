package com.gomirai.gateway.resilience;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * Wraps downstream proxy calls with circuit breaker (outer) then bulkhead (inner):
 * open circuit → fail without consuming bulkhead slots; bulkhead full → immediate reject.
 */
@Service
public class GatewayDownstreamInvoker {

    private static final Logger log = LoggerFactory.getLogger(GatewayDownstreamInvoker.class);

    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;

    public GatewayDownstreamInvoker(BulkheadRegistry bulkheadRegistry, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.bulkhead = bulkheadRegistry.bulkhead("gatewayDownstream");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("gatewayDownstream");
    }

    public ResponseEntity<byte[]> invoke(Supplier<ResponseEntity<byte[]>> downstreamCall) {
        try {
            Supplier<ResponseEntity<byte[]>> withBulkhead = Bulkhead.decorateSupplier(bulkhead, downstreamCall);
            Supplier<ResponseEntity<byte[]>> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, withBulkhead);
            return withCb.get();
        } catch (BulkheadFullException e) {
            log.warn("SLA_BULKHEAD_REJECT gatewayDownstream: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body("{\"error\":\"GATEWAY_OVERLOADED\"}".getBytes());
        } catch (CallNotPermittedException e) {
            log.warn("SLA_CIRCUIT_OPEN gatewayDownstream: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body("{\"error\":\"GATEWAY_CIRCUIT_OPEN\"}".getBytes());
        }
    }
}

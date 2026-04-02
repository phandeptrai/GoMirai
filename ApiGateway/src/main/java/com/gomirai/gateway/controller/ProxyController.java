package com.gomirai.gateway.controller;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gomirai.common.enums.ServiceName;
import com.gomirai.common.security.GatewayDelegationAuthenticationFilter;
import com.gomirai.common.security.InternalApiKeyFilter;

/**
 * Reverse-proxy controller for the API Gateway.
 *
 * <h3>Routing algorithm</h3>
 * <pre>
 *   1. Extract {serviceId} from /api/{serviceId}/**
 *   2. Resolve ServiceName via ServiceName.fromPath(serviceId)
 *      – plural aliases (drivers → driver) are handled automatically
 *   3. Choose a healthy instance from Consul via LoadBalancerClient
 *   4. Reconstruct the downstream URL:
 *        http://{host}:{port}{service.pathPrefix}/{remaining}
 *   5. Forward with all security headers applied
 * </pre>
 *
 * <h3>Performance: Non-blocking forward via WebClient</h3>
 * <p>Calls {@code .block()} are intentionally avoided outside of error paths.
 * The gateway runs on Spring MVC with Virtual Threads (spring.threads.virtual.enabled=true),
 * so blocking on the Virtual Thread carrier is acceptable; however, to minimize
 * thread-hold time we now use a dedicated connection pool (see {@link GatewayWebClientConfig})
 * with controlled concurrency and timeouts instead of per-request connections.
 *
 * <h3>Single source of truth</h3>
 * All service-name ↔ path-prefix mappings live exclusively in
 * {@link ServiceName}. This controller contains NO hardcoded strings
 * for individual services.
 */
@Controller
public class ProxyController {

    private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

    /**
     * Downstream timeout budget:
     * – 25 s for normal requests
     * – Large enough to cover slow Saga enrichment but short enough to surface real hung services.
     */
    private static final Duration PROXY_TIMEOUT = Duration.ofSeconds(25);

    private static final String GATEWAY_ROUTE_PATTERN = "/api/{serviceId}/**";

    private final LoadBalancerClient loadBalancerClient;
    private final WebClient gatewayWebClient;
    private final String internalApiKey;

    public ProxyController(
            LoadBalancerClient loadBalancerClient,
            WebClient gatewayWebClient,
            @Value("${security.internal.api-key}") String internalApiKey) {
        this.loadBalancerClient = loadBalancerClient;
        this.gatewayWebClient   = gatewayWebClient;
        this.internalApiKey     = internalApiKey;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Main proxy handler
    // ──────────────────────────────────────────────────────────────────────────

    @RequestMapping(path = GATEWAY_ROUTE_PATTERN)
    @ResponseBody
    public ResponseEntity<byte[]> proxyApi(
            HttpServletRequest request,
            @PathVariable("serviceId") String serviceId) {

        // 1. Resolve service — handles plural aliases transparently
        ServiceName service;
        try {
            service = ServiceName.fromPath(serviceId);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown service path segment '{}': {}", serviceId, e.getMessage());
            return ResponseEntity.status(404)
                    .body(("Unknown service: " + serviceId).getBytes());
        }

        try {
            // 2. Consul service discovery
            ServiceInstance instance = loadBalancerClient.choose(service.getConsulName());
            if (instance == null) {
                logger.warn("No healthy instance found in Consul for '{}'", service.getConsulName());
                return ResponseEntity.status(503)
                        .body(("Service unavailable: " + service.getConsulName()).getBytes());
            }

            // 3. Build downstream URL
            URI target = buildTargetUri(request, serviceId, service, instance);

            // 4. Build headers — filter hop-by-hop then inject gateway delegation headers
            HttpMethod method  = HttpMethod.valueOf(request.getMethod());
            HttpHeaders headers = filterRequestHeaders(request);
            applyGatewayDelegatedHeaders(headers);

            logger.info("Proxying {} {} → {} [{}]",
                    method, request.getRequestURI(), target, service.getConsulName());

            // 5. Forward — the WebClient keeps a warm connection pool (see GatewayWebClientConfig)
            //    Virtual Threads make the .block() below cheap: the VT is parked (not a carrier thread).
            return forwardWithWebClient(target, method, request, headers);

        } catch (Exception e) {
            logger.error("CRITICAL Proxy error for service '{}': {}",
                    service.getConsulName(), e.getMessage(), e);
            return ResponseEntity.status(502)
                    .body("{\"error\":\"Bad Gateway\"}".getBytes());
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // URL construction
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Build the downstream URI.
     *
     * <p>The downstream path is:
     * <pre>
     *   {service.pathPrefix} + "/" + {remaining sub-path after /api/{serviceId}/}
     * </pre>
     *
     * Examples:
     * <pre>
     *   /api/auth/login     → auth-service  → /auth/login
     *   /api/driver/me      → driver-service → /api/driver/me
     *   /api/drivers/apply  → driver-service → /api/driver/apply  (plural normalized)
     * </pre>
     */
    private URI buildTargetUri(
            HttpServletRequest request,
            String rawServiceId,
            ServiceName service,
            ServiceInstance instance) {

        String requestUri = request.getRequestURI();
        AntPathMatcher matcher = new AntPathMatcher();

        // Extract the sub-path after /api/{rawServiceId}/
        String remaining = matcher.extractPathWithinPattern(
                "/api/" + rawServiceId + "/**", requestUri);

        // Combine pathPrefix + remaining
        String targetPath = service.getPathPrefix();
        if (remaining != null && !remaining.isEmpty()) {
            targetPath = targetPath + (remaining.startsWith("/") ? remaining : "/" + remaining);
        }

        String query = request.getQueryString();
        String url = String.format("http://%s:%d%s%s",
                instance.getHost(),
                instance.getPort(),
                targetPath,
                (query != null ? "?" + query : ""));

        return URI.create(url);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // HTTP forwarding  (Virtual-Thread-aware blocking)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Forward the request and collect the downstream response.
     *
     * <p><b>Why .block() is safe here:</b>
     * Spring MVC with {@code spring.threads.virtual.enabled=true} dispatches each
     * request on a Virtual Thread. Calling {@code .block()} parks the VT (not a
     * platform thread), so no OS thread is wasted while waiting for the downstream
     * service. The downstream connection is reused from the warm Netty pool
     * configured in {@link GatewayWebClientConfig}, eliminating TCP handshake
     * overhead on the hot path.
     *
     * <p><b>Connection pool sizing (see GatewayWebClientConfig):</b>
     * max=500 connections, pendingAcquireMaxCount=2_000 → can queue bursts of
     * 2 000 concurrent requests without spinning up new OS threads.
     */
    private ResponseEntity<byte[]> forwardWithWebClient(
            URI target,
            HttpMethod method,
            HttpServletRequest request,
            HttpHeaders headers) throws IOException {

        WebClient.RequestBodySpec spec = gatewayWebClient
                .method(method)
                .uri(target)
                .headers(h -> h.addAll(headers));

        WebClient.RequestHeadersSpec<?> specWithBody = spec;
        if (method != HttpMethod.GET
                && method != HttpMethod.HEAD
                && method != HttpMethod.OPTIONS) {

            long len = request.getContentLengthLong();
            InputStreamResource resource = new InputStreamResource(request.getInputStream()) {
                @Override
                public long contentLength() { return len; }
            };
            specWithBody = spec.body(BodyInserters.fromResource(resource));
        }

        return specWithBody.exchangeToMono(response -> {
            HttpHeaders resHeaders = new HttpHeaders();
            response.headers().asHttpHeaders().forEach((name, values) -> {
                if (!name.equalsIgnoreCase("Transfer-Encoding")
                        && !name.equalsIgnoreCase("Content-Length")) {
                    resHeaders.addAll(name, values);
                }
            });
            return response.bodyToMono(byte[].class)
                    .map(body -> ResponseEntity.status(response.statusCode())
                            .headers(resHeaders).body(body))
                    .defaultIfEmpty(ResponseEntity.status(response.statusCode())
                            .headers(resHeaders).build());
        }).block(PROXY_TIMEOUT);       // VT-safe park — see Javadoc above
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Security header helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Inject gateway-delegation headers so downstream services can trust
     * the authenticated userId and role without re-validating the JWT.
     */
    private void applyGatewayDelegatedHeaders(HttpHeaders headers) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return;
        }

        headers.set(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER, internalApiKey);

        if (auth.getPrincipal() instanceof UUID userId) {
            headers.set(GatewayDelegationAuthenticationFilter.GATEWAY_USER_ID_HEADER,
                    userId.toString());

            // Pick highest-priority role: ADMIN > DRIVER > CUSTOMER
            auth.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .filter(a -> a.startsWith("ROLE_"))
                    .sorted((a1, a2) -> {
                        if ("ROLE_ADMIN".equals(a1))  return -1;
                        if ("ROLE_ADMIN".equals(a2))  return 1;
                        if ("ROLE_DRIVER".equals(a1)) return -1;
                        if ("ROLE_DRIVER".equals(a2)) return 1;
                        return 0;
                    })
                    .findFirst()
                    .map(role -> role.startsWith("ROLE_") ? role.substring(5) : role)
                    .ifPresent(roleName ->
                            headers.set(GatewayDelegationAuthenticationFilter.GATEWAY_USER_ROLE_HEADER,
                                    roleName));
        }

        // Strip raw JWT — downstream services MUST NOT re-validate it
        headers.remove(HttpHeaders.AUTHORIZATION);
    }

    /**
     * Remove hop-by-hop headers and any headers that must be controlled
     * exclusively by the gateway security layer.
     */
    private HttpHeaders filterRequestHeaders(HttpServletRequest request) {
        HttpHeaders filtered = new HttpHeaders();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!isHopByHop(name)) {
                Collections.list(request.getHeaders(name))
                           .forEach(val -> filtered.add(name, val));
            }
        });
        return filtered;
    }

    private boolean isHopByHop(String name) {
        String lower = name.toLowerCase();
        return lower.equals("connection")
            || lower.equals("keep-alive")
            || lower.equals("proxy-authenticate")
            || lower.equals("proxy-authorization")
            || lower.equals("te")
            || lower.equals("trailer")
            || lower.equals("transfer-encoding")
            || lower.equals("upgrade")
            || lower.equals("host")
            || lower.equals("content-length")
            || lower.equals("origin")
            || lower.equals("referer")
            // Strip security headers that the client must never spoof
            || lower.equalsIgnoreCase(InternalApiKeyFilter.INTERNAL_API_KEY_HEADER)
            || lower.equalsIgnoreCase(GatewayDelegationAuthenticationFilter.GATEWAY_USER_ID_HEADER)
            || lower.equalsIgnoreCase(GatewayDelegationAuthenticationFilter.GATEWAY_USER_ROLE_HEADER);
    }
}

package com.gomirai.ride.booking.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.gomirai.common.security.InternalApiKeyInterceptor;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * RestTemplate configuration using Apache HttpClient 5 with connection pooling.
 *
 * <h3>PERF FIX — Replace SimpleClientHttpRequestFactory (no pooling) with Apache HttpClient pool</h3>
 * <ul>
 *   <li><b>SimpleClientHttpRequestFactory</b>: opens a NEW TCP connection per request → O(n) handshake overhead.
 *       Under 500 VUs × 2 downstream calls (map+pricing) = up to 1000 concurrent TCP handshakes.</li>
 *   <li><b>PoolingHttpClientConnectionManager</b>: reuses connections → warm pool, no handshake on hot path.</li>
 *   <li><b>maxTotal=400, maxPerRoute=200</b>: matches bulkhead max-concurrent-calls=600 so connections
 *       are never the bottleneck before bulkhead acts.</li>
 *   <li><b>keepAlive 30s</b>: releases idle connections before downstream pods restart (avoids "connection reset" errors).</li>
 * </ul>
 */
@Configuration
public class RestTemplateConfig {

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    @Value("${booking.map-service.connect-timeout:2000}")
    private int mapConnectTimeout;

    @Value("${booking.map-service.read-timeout:5000}")
    private int mapReadTimeout;

    @Value("${booking.pricing-service.connect-timeout:2000}")
    private int pricingConnectTimeout;

    @Value("${booking.pricing-service.read-timeout:5000}")
    private int pricingReadTimeout;

    @Value("${booking.payment-service.connect-timeout:2000}")
    private int paymentConnectTimeout;

    @Value("${booking.payment-service.read-timeout:5000}")
    private int paymentReadTimeout;

    @Value("${booking.tracking-service.connect-timeout:2000}")
    private int trackingConnectTimeout;

    @Value("${booking.tracking-service.read-timeout:5000}")
    private int trackingReadTimeout;

    // Apache HttpClient pool sizing (configured via application.properties)
    @Value("${booking.http-client.max-total-connections:400}")
    private int maxTotalConnections;

    @Value("${booking.http-client.max-connections-per-route:200}")
    private int maxConnectionsPerRoute;

    @Value("${booking.http-client.connection-keep-alive-seconds:30}")
    private int connectionKeepAliveSeconds;

    @Bean(name = "mapServiceRestTemplate")
    public RestTemplate mapServiceRestTemplate() {
        return createRestTemplate(mapConnectTimeout, mapReadTimeout);
    }

    @Bean(name = "pricingServiceRestTemplate")
    public RestTemplate pricingServiceRestTemplate() {
        return createRestTemplate(pricingConnectTimeout, pricingReadTimeout);
    }

    @Bean(name = "paymentServiceRestTemplate")
    public RestTemplate paymentServiceRestTemplate() {
        return createRestTemplate(paymentConnectTimeout, paymentReadTimeout);
    }

    @Bean(name = "trackingServiceRestTemplate")
    public RestTemplate trackingServiceRestTemplate() {
        return createRestTemplate(trackingConnectTimeout, trackingReadTimeout);
    }

    @Bean
    public RestTemplate restTemplate() {
        return createRestTemplate(2000, 5000);
    }

    /**
     * Creates a pooled RestTemplate using Apache HttpClient 5.
     *
     * <p>Key differences from SimpleClientHttpRequestFactory:
     * <ul>
     *   <li>maxTotal connections shared across all instances of the same bean</li>
     *   <li>maxPerRoute limits connections to a single downstream service</li>
     *   <li>evictExpiredConnections() prevents stale connections from surfacing as errors</li>
     *   <li>connectionKeepAlive limits idle connection lifetime — avoids "connection reset" from restarted pods</li>
     * </ul>
     */
    private RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        // Pooled connection manager — the core fix
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxTotalConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
                .setResponseTimeout(Timeout.of(readTimeoutMs, TimeUnit.MILLISECONDS))
                .setConnectionRequestTimeout(Timeout.of(connectTimeoutMs, TimeUnit.MILLISECONDS))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                // Release expired connections — avoids "connection reset by peer" from restarted pods
                .evictExpiredConnections()
                // Release connections idle longer than keep-alive window
                .evictIdleConnections(TimeValue.of(connectionKeepAliveSeconds, TimeUnit.SECONDS))
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
        return restTemplate;
    }
}

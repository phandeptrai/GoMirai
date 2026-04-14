package com.gomirai.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;

/**
 * WebClient bean used by ProxyController to forward requests to downstream services.
 *
 * <h3>Connection-pool tuning rationale (24 vCPU / GKE e2-standard quota)</h3>
 * <ul>
 *   <li><b>maxConnections=400</b>: keeps ≈200 warm connections per pod (2 api-gateway replicas)
 *       without exhausting the downstream service's accept queue.</li>
 *   <li><b>pendingAcquireMaxCount=2_000</b>: allows burst queueing while preventing OOM;
 *       callers block on a VT (not a carrier thread) while waiting.</li>
 *   <li><b>responseTimeout</b> (default 5 s): per single downstream attempt (gateway proxy retry
 *       disabled by default; see {@code gateway.proxy.max-retries} and docs/SLA-ARCHITECTURE.md).</li>
 *   <li><b>connectTimeout</b> (default 2 s): fast-fail on dead pods; aligns with SLA stack math.</li>
 *   <li><b>maxIdleTime=30 s / evictInBackground=60 s</b>: prevents stale connections to
 *       restarted pods from silently returning errors.</li>
 * </ul>
 */
@Configuration
public class GatewayWebClientConfig {

	@Value("${gateway.downstream.max-connections:1200}")
	private int maxConnections;

	@Value("${gateway.downstream.pending-acquire-max-count:10000}")
	private int pendingAcquireMaxCount;

	@Value("${gateway.downstream.pending-acquire-timeout-seconds:10}")
	private int pendingAcquireTimeoutSeconds;

	@Value("${gateway.downstream.response-timeout-seconds:5}")
	private int responseTimeoutSeconds;

	@Value("${gateway.downstream.connect-timeout-ms:2000}")
	private int connectTimeoutMs;

	@Bean
	public WebClient gatewayWebClient() {
		ConnectionProvider provider = ConnectionProvider.builder("gateway-downstream")
				.maxConnections(maxConnections)
				.pendingAcquireMaxCount(pendingAcquireMaxCount)
				// Release idle connections before pods restart and cause "connection reset" errors
				.maxIdleTime(Duration.ofSeconds(30))
				// Evict stale idle connections in background so they don't surface as errors
				.evictInBackground(Duration.ofSeconds(60))
				// Fail fast on queue starvation instead of silently hanging
				.pendingAcquireTimeout(Duration.ofSeconds(pendingAcquireTimeoutSeconds))
				.build();

		HttpClient httpClient = HttpClient.create(provider)
				.responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
				// Fast-fail on dead/unreachable backend pods
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
				// Keep-alive so connections are reused across requests (reduces TCP handshake overhead)
				.keepAlive(true);

		return WebClient.builder()
				// 10 MB in-memory buffer — enough for typical ride payloads and multipart forms
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}
}

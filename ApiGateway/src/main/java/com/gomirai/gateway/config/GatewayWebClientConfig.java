package com.gomirai.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

/**
 * WebClient bean used by ProxyController to forward requests to downstream services.
 *
 * <h3>Connection-pool tuning rationale (24 vCPU / GKE e2-standard quota)</h3>
 * <ul>
 *   <li><b>maxConnections=400</b>: keeps ≈200 warm connections per pod (2 api-gateway replicas)
 *       without exhausting the downstream service's accept queue.</li>
 *   <li><b>pendingAcquireMaxCount=2_000</b>: allows burst queueing while preventing OOM;
 *       callers block on a VT (not a carrier thread) while waiting.</li>
 *   <li><b>responseTimeout=20 s</b>: tighter than the proxy-level 25 s timeout so Netty
 *       releases the connection before the outer .block() times out.</li>
 *   <li><b>connectTimeout=5 s</b>: fast-fail on dead pods; Consul will stop routing to them
 *       within one health-check interval anyway.</li>
 *   <li><b>maxIdleTime=30 s / evictInBackground=60 s</b>: prevents stale connections to
 *       restarted pods from silently returning errors.</li>
 * </ul>
 */
@Configuration
public class GatewayWebClientConfig {

	@Bean
	public WebClient gatewayWebClient() {
		ConnectionProvider provider = ConnectionProvider.builder("gateway-downstream")
				// 400 = enough for 2 gateway pods × 200 concurrent upstream connections each
				.maxConnections(400)
				// Queue up to 2 000 acquire requests during load spikes
				.pendingAcquireMaxCount(2_000)
				// Release idle connections before pods restart and cause "connection reset" errors
				.maxIdleTime(Duration.ofSeconds(30))
				// Evict stale idle connections in background so they don't surface as errors
				.evictInBackground(Duration.ofSeconds(60))
				// Fail fast on queue starvation instead of silently hanging
				.pendingAcquireTimeout(Duration.ofSeconds(20))
				.build();

		HttpClient httpClient = HttpClient.create(provider)
				// Must be shorter than the 25 s proxy-level timeout so the connection
				// can be returned to the pool before the block() call times out.
				.responseTimeout(Duration.ofSeconds(20))
				// Fast-fail on dead/unreachable backend pods
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
				// Keep-alive so connections are reused across requests (reduces TCP handshake overhead)
				.keepAlive(true);

		return WebClient.builder()
				// 10 MB in-memory buffer — enough for typical ride payloads and multipart forms
				.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
				.clientConnector(new ReactorClientHttpConnector(httpClient))
				.build();
	}
}

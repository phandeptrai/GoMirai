package com.gomirai.gateway.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.gomirai.common.security.InternalApiKeyInterceptor;

import java.util.Collections;

/**
 * RestTemplate configuration that enables HTTP PATCH support via Apache HttpClient
 * and adds Internal API Key for service-to-service calls.
 */
// @Configuration
public class RestTemplateConfig {

	@Value("${security.internal.api-key}")
	private String internalApiKey;

	@Value("${gateway.rest-template.connect-timeout-ms:2000}")
	private int connectTimeoutMs;

	@Value("${gateway.rest-template.response-timeout-ms:5000}")
	private int responseTimeoutMs;

	@Bean
	public RestTemplate restTemplate() {
		RequestConfig rc = RequestConfig.custom()
				.setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
				.setResponseTimeout(Timeout.ofMilliseconds(responseTimeoutMs))
				.build();
		CloseableHttpClient httpClient = HttpClients.custom()
				.setDefaultRequestConfig(rc)
				.build();
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
		return restTemplate;
	}
}

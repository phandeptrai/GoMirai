package com.gomirai.gateway.config;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.gomirai.common.security.InternalApiKeyInterceptor;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

/**
 * RestTemplate configuration that enables HTTP PATCH support via Apache HttpClient
 * and adds Internal API Key for service-to-service calls.
 */
@Configuration
public class RestTemplateConfig {

	@Value("${security.internal.api-key}")
	private String internalApiKey;

	@Bean
	public RestTemplate restTemplate() {
		CloseableHttpClient httpClient = HttpClients.custom().build();
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
		requestFactory.setHttpClient(httpClient);
		
		RestTemplate restTemplate = new RestTemplate(requestFactory);
		restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
		return restTemplate;
	}
}

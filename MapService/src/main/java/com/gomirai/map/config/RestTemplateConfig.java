package com.gomirai.map.config;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.gomirai.common.security.InternalApiKeyInterceptor;

@Configuration
public class RestTemplateConfig {

	@Value("${security.internal.api-key}")
	private String internalApiKey;

	@Bean
	public RestTemplate restTemplate() {
		RestTemplate restTemplate = new RestTemplate();
		restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
		restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
		return restTemplate;
	}
}

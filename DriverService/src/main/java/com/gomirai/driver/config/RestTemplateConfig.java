package com.gomirai.driver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.gomirai.common.security.InternalApiKeyInterceptor;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

@Configuration
public class RestTemplateConfig {
    
    @Value("${security.internal.api-key}")
    private String internalApiKey;

    @Value("${driver.rest-template.connect-timeout-ms:2000}")
    private int connectTimeoutMs;

    @Value("${driver.rest-template.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
        return restTemplate;
    }
}

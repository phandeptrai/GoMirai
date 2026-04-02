package com.gomirai.driver.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import com.gomirai.common.security.InternalApiKeyInterceptor;
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

@Configuration
public class RestTemplateConfig {
    
    @Value("${security.internal.api-key}")
    private String internalApiKey;

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
        return restTemplate;
    }
}

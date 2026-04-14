package com.gomirai.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OAuthRestTemplateConfig {

    @Bean(name = "googleOAuthRestTemplate")
    public RestTemplate googleOAuthRestTemplate(
            @Value("${google.oauth.http.connect-timeout-ms:2000}") int connectMs,
            @Value("${google.oauth.http.read-timeout-ms:5000}") int readMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}

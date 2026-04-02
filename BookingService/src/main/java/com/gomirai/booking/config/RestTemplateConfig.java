package com.gomirai.booking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import com.gomirai.common.security.InternalApiKeyInterceptor;
import java.util.Collections;

@Configuration
public class RestTemplateConfig {

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    @Value("${booking.map-service.timeout:2000}")
    private int mapTimeout;

    @Value("${booking.pricing-service.timeout:3000}")
    private int pricingTimeout;

    @Value("${booking.payment-service.timeout:10000}")
    private int paymentTimeout;

    @Value("${booking.tracking-service.timeout:5000}")
    private int trackingTimeout;

    @Bean(name = "mapServiceRestTemplate")
    public RestTemplate mapServiceRestTemplate() {
        return createRestTemplate(mapTimeout);
    }

    @Bean(name = "pricingServiceRestTemplate")
    public RestTemplate pricingServiceRestTemplate() {
        return createRestTemplate(pricingTimeout);
    }

    @Bean(name = "paymentServiceRestTemplate")
    public RestTemplate paymentServiceRestTemplate() {
        return createRestTemplate(paymentTimeout);
    }

    @Bean(name = "trackingServiceRestTemplate")
    public RestTemplate trackingServiceRestTemplate() {
        return createRestTemplate(trackingTimeout);
    }

    @Bean
    public RestTemplate restTemplate() {
        // Fallback or generic restTemplate (default 5s)
        return createRestTemplate(5000);
    }

    private RestTemplate createRestTemplate(int timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(Collections.singletonList(new InternalApiKeyInterceptor(internalApiKey)));
        return restTemplate;
    }
}

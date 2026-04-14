package com.gomirai.gateway.controller;

import java.net.URI;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

/**
 * HealthProxyController - Proxy health check requests to microservices
 */
@Controller
public class HealthProxyController {

    private final LoadBalancerClient loadBalancerClient;
    private final RestTemplate restTemplate;

    public HealthProxyController(LoadBalancerClient loadBalancerClient, RestTemplate restTemplate) {
        this.loadBalancerClient = loadBalancerClient;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/health/{serviceId}")
    @ResponseBody
    public ResponseEntity<String> proxyHealth(@PathVariable("serviceId") String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            return ResponseEntity.badRequest().body("Missing serviceId");
        }
        String normalized = serviceId.toLowerCase();
        ServiceInstance instance = loadBalancerClient.choose(normalized);
        if (instance == null) {
            return ResponseEntity.status(404).body("Service not found: " + normalized);
        }
        URI target = URI.create(String.format("http://%s:%d/actuator/health", instance.getHost(), instance.getPort()));
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(target, HttpMethod.GET, req, String.class);
            return ResponseEntity.status(resp.getStatusCode()).headers(resp.getHeaders()).body(resp.getBody());
        } catch (Exception ex) {
            return ResponseEntity.status(502).body("Bad Gateway: " + ex.getMessage());
        }
    }
}

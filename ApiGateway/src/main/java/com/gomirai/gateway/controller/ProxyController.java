package com.gomirai.gateway.controller;

import java.net.URI;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

@Controller
public class ProxyController {

	private final LoadBalancerClient loadBalancerClient;
	private final RestTemplate restTemplate = new RestTemplate();

	public ProxyController(LoadBalancerClient loadBalancerClient) {
		this.loadBalancerClient = loadBalancerClient;
	}

	// Dynamic proxy: /api/{serviceId}/** -> http://{serviceHost}:{port}/{remainingPath}
	@RequestMapping(path = "/api/{serviceId}/**")
	@ResponseBody
	public ResponseEntity<byte[]> proxyApi(HttpServletRequest request, @PathVariable("serviceId") String serviceId) throws Exception {
		if (!StringUtils.hasText(serviceId)) {
			return ResponseEntity.badRequest().body("Missing serviceId".getBytes());
		}
		String normalized = serviceId.toLowerCase();
		ServiceInstance instance = loadBalancerClient.choose(normalized);
		if (instance == null) {
			return ResponseEntity.status(404).body(("Service not found: " + normalized).getBytes());
		}

		String requestUri = request.getRequestURI();
		String pattern = "/api/" + serviceId + "/**";
		AntPathMatcher matcher = new AntPathMatcher();
		String remaining = matcher.extractPathWithinPattern(pattern, requestUri);
		String query = request.getQueryString();
		String targetPath = (remaining == null || remaining.isEmpty()) ? "" : "/" + remaining;
		String full = String.format("http://%s:%d%s%s%s",
			instance.getHost(),
			instance.getPort(),
			targetPath.startsWith("/") ? targetPath : "/" + targetPath,
			(query != null && !query.isEmpty()) ? "?" : "",
			(query != null) ? query : "");
		URI target = URI.create(full);

		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		HttpHeaders headers = new HttpHeaders();
		Collections.list(request.getHeaderNames()).forEach(h -> headers.add(h, request.getHeader(h)));
		if (headers.getContentType() == null) {
			headers.setContentType(MediaType.APPLICATION_JSON);
		}
		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

		ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, httpEntity, byte[].class);
		return ResponseEntity.status(resp.getStatusCode()).headers(resp.getHeaders()).body(resp.getBody());
	}
}



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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class ProxyController {

	private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);
	
	private final LoadBalancerClient loadBalancerClient;
	private final RestTemplate restTemplate = new RestTemplate();

	public ProxyController(LoadBalancerClient loadBalancerClient) {
		this.loadBalancerClient = loadBalancerClient;
	}

	@RequestMapping(path = "/api/{serviceId}/**")
	@ResponseBody
	public ResponseEntity<byte[]> proxyApi(HttpServletRequest request, @PathVariable("serviceId") String serviceId) throws Exception {
		if (!StringUtils.hasText(serviceId)) {
			return ResponseEntity.badRequest().body("Missing serviceId".getBytes());
		}
		
		String actualServiceName = mapServiceName(serviceId);
		ServiceInstance instance = loadBalancerClient.choose(actualServiceName);
		if (instance == null) {
			return ResponseEntity.status(404).body(("Service not found: " + actualServiceName).getBytes());
		}

		String requestUri = request.getRequestURI();
		String pattern = "/api/" + serviceId + "/**";
		AntPathMatcher matcher = new AntPathMatcher();
		String remaining = matcher.extractPathWithinPattern(pattern, requestUri);
		String query = request.getQueryString();
		String servicePathPrefix = getServicePathPrefix(serviceId);
		
		String targetPath;
		if (remaining == null || remaining.isEmpty()) {
			targetPath = servicePathPrefix;
		} else {
			String normalizedRemaining = remaining.startsWith("/") ? remaining : "/" + remaining;
			targetPath = servicePathPrefix + normalizedRemaining;
		}
		
		if (!targetPath.startsWith("/")) {
			targetPath = "/" + targetPath;
		}
		
		String full = String.format("http://%s:%d%s%s%s",
			instance.getHost(),
			instance.getPort(),
			targetPath,
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

		try {
			ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, httpEntity, byte[].class);
			logger.info("Proxied {} {} -> {} (status: {})", method, requestUri, target, resp.getStatusCode());
			HttpHeaders filteredHeaders = filterHeaders(resp.getHeaders());
			return ResponseEntity.status(resp.getStatusCode()).headers(filteredHeaders).body(resp.getBody());
		} catch (HttpStatusCodeException e) {
			byte[] errorBody = e.getResponseBodyAsByteArray();
			logger.warn("Backend error: {} {} -> {} (status: {})", 
				method, requestUri, target, e.getStatusCode());
			logger.debug("Error body: {}", new String(errorBody));
			
			HttpHeaders responseHeaders = filterHeaders(e.getResponseHeaders());
			if (responseHeaders.getContentType() == null) {
				responseHeaders.setContentType(MediaType.APPLICATION_JSON);
			}
			
			return ResponseEntity.status(e.getStatusCode())
				.headers(responseHeaders)
				.body(errorBody);
		} catch (Exception e) {
			logger.error("Gateway error: {} {} -> {}", method, requestUri, target, e);
			String errorMessage = String.format("{\"error\":\"Gateway Error\",\"message\":\"%s\"}", e.getMessage());
			return ResponseEntity.status(502)
				.contentType(MediaType.APPLICATION_JSON)
				.body(errorMessage.getBytes());
		}
	}

	private HttpHeaders filterHeaders(HttpHeaders originalHeaders) {
		HttpHeaders filtered = new HttpHeaders();
		if (originalHeaders == null) {
			return filtered;
		}
		
		String[] skipHeaders = {
			"Transfer-Encoding", "Connection", "Keep-Alive",
			"Proxy-Authenticate", "Proxy-Authorization",
			"TE", "Trailer", "Upgrade", "Content-Length"
		};
		
		originalHeaders.forEach((key, value) -> {
			boolean shouldSkip = false;
			for (String skipHeader : skipHeaders) {
				if (skipHeader.equalsIgnoreCase(key)) {
					shouldSkip = true;
					break;
				}
			}
			if (!shouldSkip) {
				filtered.put(key, value);
			}
		});
		
		return filtered;
	}

	private String mapServiceName(String serviceId) {
		if (serviceId == null || serviceId.isEmpty()) {
			return serviceId;
		}
		String normalized = serviceId.toLowerCase();
		switch (normalized) {
			case "auth":
				return "AuthService";
			case "user":
			case "users":
				return "UserService";
			default:
				if (serviceId.length() > 0) {
					return serviceId.substring(0, 1).toUpperCase() + 
					       (serviceId.length() > 1 ? serviceId.substring(1).toLowerCase() : "") + 
					       "Service";
				}
				return serviceId;
		}
	}

	private String getServicePathPrefix(String serviceId) {
		if (serviceId == null || serviceId.isEmpty()) {
			return "";
		}
		String normalized = serviceId.toLowerCase();
		switch (normalized) {
			case "auth":
				return "/auth";
			case "user":
			case "users":
				return "/api/users";
			default:
				return "/" + serviceId;
		}
	}
}

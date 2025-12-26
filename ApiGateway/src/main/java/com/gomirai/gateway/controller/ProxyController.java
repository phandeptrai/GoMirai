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

/**
 * ProxyController - Main API Gateway Router
 * Routes ALL API requests to microservices
 */
@Controller
public class ProxyController {

	private static final Logger logger = LoggerFactory.getLogger(ProxyController.class);

	private final LoadBalancerClient loadBalancerClient;
	private final RestTemplate restTemplate;

	public ProxyController(LoadBalancerClient loadBalancerClient, RestTemplate restTemplate) {
		this.loadBalancerClient = loadBalancerClient;
		this.restTemplate = restTemplate;
	}

	@RequestMapping(path = "/api/{serviceId}/**")
	@ResponseBody
	public ResponseEntity<byte[]> proxyApi(HttpServletRequest request, @PathVariable("serviceId") String serviceId)
			throws Exception {
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
		HttpHeaders headers = filterRequestHeaders(request);
		if (headers.getContentType() == null) {
			headers.setContentType(MediaType.APPLICATION_JSON);
		}
		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

		try {
			ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, httpEntity, byte[].class);
			// ✅ SECURITY: Log without full target URL to avoid logging sensitive query
			// params
			logger.info("Proxied {} {} to service {} (status: {})", method, requestUri, actualServiceName,
					resp.getStatusCode());
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
		} catch (IllegalArgumentException e) {
			// Invalid service ID or configuration
			logger.warn("Invalid request: {} {}", method, requestUri);
			return ResponseEntity.status(400)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"error\":\"Bad Request\",\"message\":\"Invalid service\"}".getBytes());
		} catch (Exception e) {
			// ✅ SECURITY: Log detailed error but return generic message to client
			logger.error("Gateway error: {} {} -> {}", method, requestUri, target, e);
			return ResponseEntity.status(502)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"error\":\"Bad Gateway\",\"message\":\"Service temporarily unavailable\"}".getBytes());
		}
	}

	/**
	 * Proxy WebSocket/SockJS requests to backend services
	 * SockJS uses HTTP for transport (info, xhr_streaming, xhr_polling, etc.)
	 * Pattern: /ws/{serviceId}/** where serviceId maps to service name
	 */
	@RequestMapping(path = "/ws/{serviceId}/**")
	@ResponseBody
	public ResponseEntity<byte[]> proxyWebSocket(HttpServletRequest request,
			@PathVariable("serviceId") String serviceId) throws Exception {
		if (!StringUtils.hasText(serviceId)) {
			return ResponseEntity.badRequest().body("Missing serviceId".getBytes());
		}

		String actualServiceName = mapServiceName(serviceId);
		ServiceInstance instance = loadBalancerClient.choose(actualServiceName);
		if (instance == null) {
			logger.error("Service {} not found for WebSocket proxy", actualServiceName);
			return ResponseEntity.status(503)
					.body(("{\"error\":\"Service not available: " + actualServiceName + "\"}").getBytes());
		}

		String requestUri = request.getRequestURI();
		String query = request.getQueryString();

		// Forward to backend service with same path
		// /ws/booking/xxx -> /ws/booking/xxx on BookingService
		String full = String.format("http://%s:%d%s%s%s",
				instance.getHost(),
				instance.getPort(),
				requestUri,
				(query != null && !query.isEmpty()) ? "?" : "",
				(query != null) ? query : "");
		URI target = URI.create(full);

		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		HttpHeaders headers = filterRequestHeaders(request);

		// SockJS may send different content types
		String contentType = request.getContentType();
		if (contentType != null) {
			headers.set("Content-Type", contentType);
		}

		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

		try {
			logger.debug("Proxying WebSocket: {} {} -> {}", method, requestUri, actualServiceName);
			ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, httpEntity, byte[].class);
			HttpHeaders filteredHeaders = filterHeaders(resp.getHeaders());
			logger.info("WebSocket proxy: {} {} -> {} (status: {})",
					method, requestUri, actualServiceName, resp.getStatusCode());
			return ResponseEntity.status(resp.getStatusCode()).headers(filteredHeaders).body(resp.getBody());
		} catch (HttpStatusCodeException e) {
			byte[] errorBody = e.getResponseBodyAsByteArray();
			logger.warn("WebSocket proxy error: {} {} -> {} (status: {})",
					method, requestUri, actualServiceName, e.getStatusCode());
			HttpHeaders responseHeaders = filterHeaders(e.getResponseHeaders());
			return ResponseEntity.status(e.getStatusCode())
					.headers(responseHeaders)
					.body(errorBody);
		} catch (IllegalArgumentException e) {
			logger.warn("Invalid WebSocket request: {} {}", method, requestUri);
			return ResponseEntity.status(400)
					.body("{\"error\":\"Invalid service\"}".getBytes());
		} catch (Exception e) {
			logger.error("WebSocket proxy error: {} {} -> {}", method, requestUri, target, e);
			return ResponseEntity.status(502)
					.body("{\"error\":\"WebSocket proxy error\"}".getBytes());
		}
	}

	/**
	 * Filter request headers before forwarding to backend services
	 * Removes browser-specific headers that shouldn't be forwarded (CORS, Origin,
	 * etc.)
	 */
	private HttpHeaders filterRequestHeaders(HttpServletRequest request) {
		HttpHeaders filtered = new HttpHeaders();

		// Headers to skip when forwarding to backend services
		String[] skipHeaders = {
				// Browser-specific headers (CORS related)
				"Origin", "Referer", "User-Agent",
				// Connection headers
				"Connection", "Keep-Alive", "Transfer-Encoding",
				"Proxy-Authenticate", "Proxy-Authorization",
				"TE", "Trailer", "Upgrade",
				// Content-Length will be set automatically by RestTemplate
				"Content-Length",
				// Host header should be set to target service
				"Host"
		};

		Collections.list(request.getHeaderNames()).forEach(headerName -> {
			boolean shouldSkip = false;
			for (String skipHeader : skipHeaders) {
				if (skipHeader.equalsIgnoreCase(headerName)) {
					shouldSkip = true;
					break;
				}
			}
			if (!shouldSkip) {
				Collections.list(request.getHeaders(headerName))
						.forEach(value -> filtered.add(headerName, value));
			}
		});

		return filtered;
	}

	/**
	 * Filter response headers before sending back to frontend
	 */
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
			throw new IllegalArgumentException("Service ID cannot be empty");
		}

		// 1. Try exact match
		if (loadBalancerClient.choose(serviceId) != null) {
			return serviceId;
		}

		String normalized = serviceId.toLowerCase();
		String capitalized = StringUtils.capitalize(normalized);

		// 2. Try "Name" + "Service" (e.g., "auth" -> "AuthService")
		String standardName = capitalized + "Service";
		if (loadBalancerClient.choose(standardName) != null) {
			return standardName;
		}

		// 3. Try handling plurals (e.g., "users" -> "UserService")
		if (normalized.endsWith("s")) {
			String singular = normalized.substring(0, normalized.length() - 1);
			String capitalizedSingular = StringUtils.capitalize(singular);
			String singularName = capitalizedSingular + "Service";
			if (loadBalancerClient.choose(singularName) != null) {
				return singularName;
			}
		}

		// 4. Try normalized match as fallback
		if (loadBalancerClient.choose(normalized) != null) {
			return normalized;
		}

		throw new IllegalArgumentException("Unknown service: " + serviceId);
	}

	private String getServicePathPrefix(String serviceId) {
		if (serviceId == null || serviceId.isEmpty()) {
			return "";
		}

		// Special handling for legacy services
		if (serviceId.equalsIgnoreCase("auth")) {
			return "/auth";
		}

		// Fully dynamic: assumes standard /api/{serviceId} pattern
		// Example: serviceId="user" -> /api/user
		return "/api/" + serviceId.toLowerCase();
	}
}

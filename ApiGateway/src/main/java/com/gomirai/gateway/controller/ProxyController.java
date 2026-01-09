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
 * ProxyController - Bộ điều phối chính của API Gateway.
 * 
 * === CHỨC NĂNG CHÍNH ===
 * Định tuyến TẤT CẢ API requests từ client đến các microservices phía sau.
 * Gateway là single entry point, client không gọi trực tiếp microservices.
 * 
 * === LUỒNG XỬ LÝ REQUEST ===
 * 1. Client gọi: GET /api/booking/me
 * 2. Gateway phân tích: serviceId = "booking", remaining = "/me"
 * 3. Service Discovery: Tìm BookingService từ Eureka
 * 4. Forward: http://BookingService:8082/api/booking/me
 * 5. Trả response về client
 * 
 * === MAPPING SERVICE NAME ===
 * Gateway tự động map serviceId sang tên service trong Eureka:
 * - "auth" → "AuthService" (hoặc "auth" nếu đăng ký trực tiếp)
 * - "users" → "UserService" (xử lý số nhiều → số ít)
 * - "booking" → "BookingService"
 * 
 * === WEBSOCKET PROXY ===
 * SockJS sử dụng HTTP transport, Gateway proxy các requests đến:
 * - /ws/notifications/** → NotificationService
 * 
 * === SECURITY ===
 * - Lọc headers nhạy cảm trước khi forward
 * - Log lỗi chi tiết nhưng trả về message generic cho client
 * - Forward JWT token trong header Authorization
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

	/**
	 * Proxy tất cả API requests đến microservices.
	 * 
	 * URL Pattern: /api/{serviceId}/**
	 * Ví dụ:
	 * - /api/auth/login → AuthService
	 * - /api/booking/me → BookingService
	 * - /api/users/{userId} → UserService
	 * 
	 * @param request   HttpServletRequest từ client
	 * @param serviceId ID của service (auth, booking, users, ...)
	 * @return Response từ microservice (giữ nguyên status code và body)
	 */
	@RequestMapping(path = "/api/{serviceId}/**")
	@ResponseBody
	public ResponseEntity<byte[]> proxyApi(HttpServletRequest request, @PathVariable("serviceId") String serviceId)
			throws Exception {
		if (!StringUtils.hasText(serviceId)) {
			return ResponseEntity.badRequest().body("Missing serviceId".getBytes());
		}

		// === BƯỚC 1: Service Discovery ===
		// Chuyển đổi serviceId sang tên service thực trong Eureka
		// VD: "booking" → "BookingService"
		String actualServiceName = mapServiceName(serviceId);
		ServiceInstance instance = loadBalancerClient.choose(actualServiceName);
		if (instance == null) {
			return ResponseEntity.status(404).body(("Service not found: " + actualServiceName).getBytes());
		}

		// === BƯỚC 2: Xây dựng đường dẫn target ===
		// Trích xuất phần path còn lại sau /api/{serviceId}
		// VD: /api/booking/me → remaining = "/me"
		String requestUri = request.getRequestURI();
		String pattern = "/api/" + serviceId + "/**";
		AntPathMatcher matcher = new AntPathMatcher();
		String remaining = matcher.extractPathWithinPattern(pattern, requestUri);
		String query = request.getQueryString();
		String servicePathPrefix = getServicePathPrefix(serviceId);

		// Ghép path prefix của service + phần remaining
		// VD: "/api/booking" + "/me" = "/api/booking/me"
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

		// === BƯỚC 3: Tạo URL hoàn chỉnh đến service ===
		// Format: http://{host}:{port}{path}?{query}
		// VD: http://192.168.1.10:8082/api/booking/me?status=PENDING
		String full = String.format("http://%s:%d%s%s%s",
				instance.getHost(),
				instance.getPort(),
				targetPath,
				(query != null && !query.isEmpty()) ? "?" : "",
				(query != null) ? query : "");
		URI target = URI.create(full);

		// === BƯỚC 4: Chuẩn bị request để forward ===
		// Lọc headers (loại bỏ headers của browser như Origin, Host)
		// Copy body nguyên bản
		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		HttpHeaders headers = filterRequestHeaders(request);
		if (headers.getContentType() == null) {
			headers.setContentType(MediaType.APPLICATION_JSON);
		}
		byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
		HttpEntity<byte[]> httpEntity = new HttpEntity<>(body, headers);

		// === BƯỚC 5: Forward request và trả response ===
		try {
			ResponseEntity<byte[]> resp = restTemplate.exchange(target, method, httpEntity, byte[].class);
			// ✅ BẢO MẬT: Log không kèm URL đầy đủ để tránh lộ query params nhạy cảm
			logger.info("Proxied {} {} to service {} (status: {})", method, requestUri, actualServiceName,
					resp.getStatusCode());
			HttpHeaders filteredHeaders = filterHeaders(resp.getHeaders());
			return ResponseEntity.status(resp.getStatusCode()).headers(filteredHeaders).body(resp.getBody());
		} catch (HttpStatusCodeException e) {
			// Xử lý lỗi HTTP từ backend (4xx, 5xx)
			// Giữ nguyên status code và body lỗi từ service
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
			// Service ID không hợp lệ hoặc không tìm thấy
			logger.warn("Invalid request: {} {}", method, requestUri);
			return ResponseEntity.status(400)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"error\":\"Bad Request\",\"message\":\"Invalid service\"}".getBytes());
		} catch (Exception e) {
			// ✅ BẢO MẬT: Log chi tiết lỗi nhưng trả message generic cho client
			// Tránh lộ thông tin internal error
			logger.error("Gateway error: {} {} -> {}", method, requestUri, target, e);
			return ResponseEntity.status(502)
					.contentType(MediaType.APPLICATION_JSON)
					.body("{\"error\":\"Bad Gateway\",\"message\":\"Service temporarily unavailable\"}".getBytes());
		}
	}

	/**
	 * Proxy WebSocket/SockJS requests đến backend services.
	 * 
	 * SockJS sử dụng HTTP làm transport (info, xhr_streaming, xhr_polling, etc.)
	 * URL Pattern: /ws/{serviceId}/**
	 * 
	 * Ví dụ:
	 * - /ws/notifications/info → NotificationService
	 * - /ws/notifications/123/abc/xhr → NotificationService
	 * 
	 * LƯU Ý: WebSocket thực sự (ws://) không đi qua đây,
	 * cần cấu hình riêng ở proxy layer (Nginx/LoadBalancer)
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

		// Forward giữ nguyên path đến backend service
		// /ws/notifications/xxx → /ws/notifications/xxx trên NotificationService
		String full = String.format("http://%s:%d%s%s%s",
				instance.getHost(),
				instance.getPort(),
				requestUri,
				(query != null && !query.isEmpty()) ? "?" : "",
				(query != null) ? query : "");
		URI target = URI.create(full);

		HttpMethod method = HttpMethod.valueOf(request.getMethod());
		HttpHeaders headers = filterRequestHeaders(request);

		// SockJS có thể gửi nhiều loại Content-Type khác nhau
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
	 * Lọc headers từ request trước khi forward đến backend services.
	 * 
	 * Loại bỏ các headers không nên forward:
	 * - Headers của browser (Origin, Referer, User-Agent) - liên quan CORS
	 * - Headers kết nối (Connection, Keep-Alive) - được quản lý bởi HTTP client
	 * - Content-Length - được tính tự động bởi RestTemplate
	 * - Host - sẽ được set lại thành host của target service
	 * 
	 * Headers được giữ lại:
	 * - Authorization (JWT token)
	 * - Content-Type
	 * - Accept
	 * - Custom headers (X-*)
	 */
	private HttpHeaders filterRequestHeaders(HttpServletRequest request) {
		HttpHeaders filtered = new HttpHeaders();

		// Danh sách headers cần loại bỏ khi forward đến backend
		String[] skipHeaders = {
				// Headers của browser (liên quan CORS)
				"Origin", "Referer", "User-Agent",
				// Headers kết nối (được quản lý bởi HTTP layer)
				"Connection", "Keep-Alive", "Transfer-Encoding",
				"Proxy-Authenticate", "Proxy-Authorization",
				"TE", "Trailer", "Upgrade",
				// Content-Length được tính tự động bởi RestTemplate
				"Content-Length",
				// Host sẽ được set thành target service host
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
	 * Lọc headers từ response trước khi trả về cho frontend.
	 * 
	 * Loại bỏ các headers kết nối/proxy không cần thiết.
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

	/**
	 * Chuyển đổi serviceId từ URL sang tên service trong Eureka.
	 * 
	 * Thử theo thứ tự:
	 * 1. Exact match: "AuthService" → "AuthService"
	 * 2. Chuẩn hóa: "auth" → "AuthService"
	 * 3. Xử lý số nhiều: "users" → "UserService" (bỏ 's' cuối)
	 * 4. Lowercase fallback: "authservice" → "authservice"
	 * 
	 * @param serviceId ID của service trong URL
	 * @return Tên service đăng ký trong Eureka
	 * @throws IllegalArgumentException nếu không tìm thấy service
	 */
	private String mapServiceName(String serviceId) {
		if (serviceId == null || serviceId.isEmpty()) {
			throw new IllegalArgumentException("Service ID cannot be empty");
		}

		// 1. Thử exact match trước
		if (loadBalancerClient.choose(serviceId) != null) {
			return serviceId;
		}

		String normalized = serviceId.toLowerCase();
		String capitalized = StringUtils.capitalize(normalized);

		// 2. Thử format chuẩn: "Name" + "Service" (VD: "auth" → "AuthService")
		String standardName = capitalized + "Service";
		if (loadBalancerClient.choose(standardName) != null) {
			return standardName;
		}

		// 3. Xử lý số nhiều: "users" → "UserService"
		if (normalized.endsWith("s")) {
			String singular = normalized.substring(0, normalized.length() - 1);
			String capitalizedSingular = StringUtils.capitalize(singular);
			String singularName = capitalizedSingular + "Service";
			if (loadBalancerClient.choose(singularName) != null) {
				return singularName;
			}
		}

		// 4. Fallback: thử normalized (lowercase) match
		if (loadBalancerClient.choose(normalized) != null) {
			return normalized;
		}

		throw new IllegalArgumentException("Unknown service: " + serviceId);
	}

	/**
	 * Lấy đường dẫn prefix của service.
	 * 
	 * Mỗi service có path prefix riêng:
	 * - AuthService: /auth (legacy, không có /api)
	 * - BookingService: /api/booking
	 * - UserService: /api/user
	 * 
	 * @param serviceId ID của service
	 * @return Path prefix của service
	 */
	private String getServicePathPrefix(String serviceId) {
		if (serviceId == null || serviceId.isEmpty()) {
			return "";
		}

		// Xử lý riêng cho auth service (legacy path)
		if (serviceId.equalsIgnoreCase("auth")) {
			return "/auth";
		}

		// Pattern chuẩn: /api/{serviceId}
		// VD: serviceId="booking" → /api/booking
		return "/api/" + serviceId.toLowerCase();
	}
}

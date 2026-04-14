package com.gomirai.driver.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.gomirai.common.security.GatewayDelegationAuthenticationFilter;
import com.gomirai.common.security.JwtAuthenticationEntryPoint;
import com.gomirai.common.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final JwtAuthenticationEntryPoint authenticationEntryPoint;

	// ✅ FIX: Đọc CORS từ ENV var thay vì hardcode localhost
	// Production: set CORS_ALLOWED_ORIGINS qua K8s ConfigMap
	// Local dev fallback: localhost ports
	@Value("${cors.allowed.origins:http://localhost:8080,http://api-gateway:8080}")
	private String allowedOrigins;

	@Value("${cors.allowed.methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
	private String allowedMethods;

	@Value("${cors.allowed.headers:*}")
	private String allowedHeaders;

	@Value("${cors.exposed.headers:Authorization}")
	private String exposedHeaders;

	@Value("${cors.allow.credentials:true}")
	private boolean allowCredentials;

	@Value("${cors.max.age:3600}")
	private long maxAge;

	@Value("${security.internal.api-key:gomirai-internal-s3cr3t-k3y-2026-ch4ng3-m3-1n-pr0ductI0n}")
	private String internalApiKey;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			JwtAuthenticationEntryPoint authenticationEntryPoint) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Bean
	public com.gomirai.common.security.InternalApiKeyFilter internalApiKeyFilter() {
		return new com.gomirai.common.security.InternalApiKeyFilter(internalApiKey);
	}

	@Bean
	public GatewayDelegationAuthenticationFilter gatewayDelegationAuthenticationFilter() {
		return new GatewayDelegationAuthenticationFilter(internalApiKey);
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/metrics").permitAll()
						// Allow WebSocket endpoints without authentication
						.requestMatchers("/ws/**").permitAll()
						// Public driver info for customer to view during trip
						.requestMatchers("/api/drivers/user/*/public").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(gatewayDelegationAuthenticationFilter(), JwtAuthenticationFilter.class)
				.addFilterBefore(internalApiKeyFilter(), GatewayDelegationAuthenticationFilter.class);

		http.headers(headers -> headers
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
				.frameOptions(frame -> frame.deny()));

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();

		// ✅ FIX: Đọc origins từ ENV var (không hardcode localhost)
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		configuration.setAllowedOrigins(origins);

		List<String> methods = Arrays.stream(allowedMethods.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		configuration.setAllowedMethods(methods);

		if ("*".equals(allowedHeaders.trim())) {
			configuration.setAllowedHeaders(Arrays.asList("*"));
		} else {
			List<String> headers = Arrays.stream(allowedHeaders.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.collect(Collectors.toList());
			configuration.setAllowedHeaders(headers);
		}

		List<String> exposed = Arrays.stream(exposedHeaders.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toList());
		configuration.setExposedHeaders(exposed);

		configuration.setAllowCredentials(allowCredentials);
		configuration.setMaxAge(maxAge);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}

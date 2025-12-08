package com.gomirai.map.config;

import java.util.Arrays;

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

import com.gomirai.common.security.JwtAuthenticationEntryPoint;
import com.gomirai.common.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtFilter;
	private final JwtAuthenticationEntryPoint authEntryPoint;

	@Value("${cors.allowed.origins}")
	private String allowedOrigins;

	@Value("${cors.allowed.methods}")
	private String allowedMethods;

	@Value("${cors.allowed.headers}")
	private String allowedHeaders;

	@Value("${cors.exposed.headers}")
	private String exposedHeaders;

	@Value("${cors.allow.credentials}")
	private boolean allowCredentials;

	@Value("${cors.max.age}")
	private long maxAge;

	// ✅ Autowire từ Common Lib (nhờ @ComponentScan)
	public SecurityConfig(JwtAuthenticationFilter jwtFilter, JwtAuthenticationEntryPoint authEntryPoint) {
		this.jwtFilter = jwtFilter;
		this.authEntryPoint = authEntryPoint;
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors(cors -> cors.configurationSource(corsConfigurationSource())) // ✅ CORS Config
			.exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health").permitAll() // Public health check
				.anyRequest().authenticated()                    // Tất cả request khác cần JWT
			)
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

		http.headers(headers -> headers
			.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
			.frameOptions(frame -> frame.deny())
		);

		return http.build();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		
		// Read from application.properties
		configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
		configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
		
		if ("*".equals(allowedHeaders)) {
			configuration.setAllowedHeaders(Arrays.asList("*"));
		} else {
			configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
		}
		
		configuration.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
		configuration.setAllowCredentials(allowCredentials);
		configuration.setMaxAge(maxAge);
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}


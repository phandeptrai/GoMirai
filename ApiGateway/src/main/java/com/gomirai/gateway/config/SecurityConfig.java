package com.gomirai.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final JwtAuthenticationEntryPoint authenticationEntryPoint;

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

        public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                        JwtAuthenticationEntryPoint authenticationEntryPoint) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.authenticationEntryPoint = authenticationEntryPoint;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http

                                // CSRF disabled vì đây là REST API với JWT (stateless)
                                .csrf(csrf -> csrf.disable())

                                // Session stateless vì dùng JWT
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // CORS configuration
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                // Exception handling
                                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint))

                                // Authorization rules
                                .authorizeHttpRequests(auth -> auth
                                                // Public endpoints
                                                .requestMatchers("/actuator/health").permitAll() // Only health endpoint
                                                .requestMatchers("/health/**").permitAll() // Service health checks

                                                // WebSocket endpoints - không cần JWT cho initial handshake
                                                .requestMatchers("/ws/**").permitAll()

                                                // CORS preflight requests - phải permit để CORS hoạt động
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // Auth endpoints - không cần JWT
                                                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                                                .requestMatchers(HttpMethod.POST, "/api/auth/google").permitAll() // Google
                                                                                                                  // OAuth

                                                // Pricing estimate endpoint - public để tính giá
                                                .requestMatchers(HttpMethod.POST, "/api/pricing/estimate").permitAll()
                                                // Cho phép GET Reviews là Public
                                                .requestMatchers(HttpMethod.GET, "/api/review/reviewee/**").permitAll()

                                                // VNPay callback endpoints - PHẢI PUBLIC vì VNPay server gọi trực tiếp
                                                .requestMatchers(HttpMethod.GET, "/api/payment/vnpay/callback")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/payment/vnpay/return")
                                                .permitAll()

                                                // Tất cả các requests khác cần authentication
                                                .anyRequest().authenticated())

                                // Add JWT filter
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                // Security headers
                http.headers(headers -> headers
                                .frameOptions(frame -> frame.sameOrigin()));

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // ✅ FIX: Trim whitespace từ mỗi origin để tránh CORS mismatch (403 Forbidden)
                List<String> origins = Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                configuration.setAllowedOrigins(origins);

                // ✅ FIX: Trim whitespace từ methods
                List<String> methods = Arrays.stream(allowedMethods.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
                configuration.setAllowedMethods(methods);

                if ("*".equals(allowedHeaders)) {
                        configuration.setAllowedHeaders(Arrays.asList("*"));
                } else {
                        // ✅ FIX: Trim whitespace từ headers
                        List<String> headers = Arrays.stream(allowedHeaders.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .collect(Collectors.toList());
                        configuration.setAllowedHeaders(headers);
                }

                // ✅ FIX: Trim whitespace từ exposed headers
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

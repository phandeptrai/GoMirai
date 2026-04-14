package com.gomirai.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.gomirai.identity.filter.RateLimitingFilter;
import com.gomirai.common.security.GatewayDelegationAuthenticationFilter;
import com.gomirai.common.security.InternalApiKeyFilter;
import com.gomirai.common.security.JwtAuthenticationFilter;

import com.gomirai.common.security.JwtAuthenticationEntryPoint;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import java.util.Arrays;

/**
 * Cấu hình bảo mật cho AuthService.
 * 
 * Các tính năng chính:
 * - JWT Authentication: Xác thực bằng token thay vì session
 * - CORS: Cho phép cross-origin requests từ các origin được cấu hình
 * - Rate Limiting: Giới hạn số request để chống brute force
 * - Security Headers: Bảo vệ chống XSS, Clickjacking, etc.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;

    // Đọc cấu hình CORS từ application.properties
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

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RateLimitingFilter rateLimitingFilter,
                          JwtAuthenticationEntryPoint authEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public GatewayDelegationAuthenticationFilter gatewayDelegationAuthenticationFilter() {
        return new GatewayDelegationAuthenticationFilter(internalApiKey);
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter() {
        return new InternalApiKeyFilter(internalApiKey);
    }

    /**
     * Cấu hình Security Filter Chain.
     * 
     * Định nghĩa các quy tắc bảo mật:
     * - Endpoints nào public (không cần auth)
     * - Endpoints nào cần authentication
     * - Thứ tự các filter
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tắt CSRF vì đây là REST API với JWT (stateless)
                // CSRF chỉ cần cho session-based authentication
                .csrf(csrf -> csrf.disable())

                // Stateless session - không lưu session trên server
                // Mỗi request phải tự mang JWT token
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Cấu hình CORS từ bean corsConfigurationSource()
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Exception handling
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))

                // Quy tắc authorization
                .authorizeHttpRequests(reg -> reg
                        // Health check endpoint - public cho monitoring
                        .requestMatchers("/actuator/health", "/actuator/prometheus", "/actuator/metrics").permitAll()
                        // Endpoints đăng ký, đăng nhập, Google OAuth - public
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/google").permitAll()
                        // Validate endpoint - public cho API Gateway gọi
                        .requestMatchers(HttpMethod.POST, "/api/auth/validate").permitAll()
                        // Public user info for other services
                        .requestMatchers("/api/users/*/public").permitAll()
                        // Tất cả request khác cần authentication
                        .anyRequest().authenticated())

                // Thêm Rate Limiting Filter TRƯỚC JWT filter
                // Để chặn brute force trước khi xử lý authentication
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayDelegationAuthenticationFilter(), JwtAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter(), GatewayDelegationAuthenticationFilter.class);

        // Cấu hình Security Headers
        http.headers(headers -> headers
                // Content Security Policy - chống XSS
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                // Frame Options - chống Clickjacking
                .frameOptions(frame -> frame.deny())
                // XSS Protection (deprecated trong Spring Security 6.1+, nhưng vẫn giữ cho
                // compatibility)
                .xssProtection(xss -> {
                }));

        return http.build();
    }

    /**
     * Cấu hình CORS (Cross-Origin Resource Sharing).
     * 
     * AuthService chỉ cho phép requests từ API Gateway,
     * không cho phép trực tiếp từ frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Đọc allowed origins từ config (VD: "http://api-gateway:8080")
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));

        // Xử lý wildcard "*" cho headers
        if ("*".equals(allowedHeaders)) {
            configuration.setAllowedHeaders(Arrays.asList("*"));
        } else {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        }

        configuration.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge); // Cache preflight trong bao lâu (seconds)

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Áp dụng cho tất cả endpoints
        return source;
    }

    /**
     * Bean mã hóa mật khẩu (BCrypt). Độ mạnh cấu hình qua {@code security.password.bcrypt-strength}
     * (mặc định 10). Giảm chỉ trên môi trường dev/load-test — production nên ≥10.
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${security.password.bcrypt-strength:10}") int bcryptStrength) {
        return new BCryptPasswordEncoder(bcryptStrength);
    }
}

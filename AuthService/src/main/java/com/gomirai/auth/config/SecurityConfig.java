package com.gomirai.auth.config;

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

import com.gomirai.auth.filter.RateLimitingFilter;
import com.gomirai.common.security.JwtAuthenticationFilter;

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
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;

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

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
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

                // Quy tắc authorization
                .authorizeHttpRequests(reg -> reg
                        // Health check endpoint - public cho monitoring
                        .requestMatchers("/actuator/health").permitAll()
                        // Endpoints đăng ký, đăng nhập, Google OAuth - public
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/google").permitAll()
                        // Validate endpoint - public cho API Gateway gọi
                        .requestMatchers(HttpMethod.POST, "/auth/validate").permitAll()
                        // Tất cả request khác cần authentication
                        .anyRequest().authenticated())

                // Thêm Rate Limiting Filter TRƯỚC JWT filter
                // Để chặn brute force trước khi xử lý authentication
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

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
     * Bean mã hóa mật khẩu.
     * Sử dụng BCrypt - thuật toán hash an toàn với salt tự động.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

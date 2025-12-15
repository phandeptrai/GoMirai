package com.gomirai.review.config;

import com.gomirai.common.security.JwtAuthenticationEntryPoint;
import com.gomirai.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authEntryPoint;

    // Giá trị @Value được inject từ application.properties
    @Value("${cors.allowed.origins:*}")
    private String allowedOrigins;
    @Value("${cors.allowed.methods:GET,POST,OPTIONS}")
    private String allowedMethods;
    @Value("${cors.allowed.headers:*}")
    private String allowedHeaders;
    @Value("${cors.exposed.headers:Authorization}")
    private String exposedHeaders;
    @Value("${cors.allow.credentials:false}")
    private boolean allowCredentials;
    @Value("${cors.max.age:3600}")
    private long maxAge;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, JwtAuthenticationEntryPoint authEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))

                .authorizeHttpRequests(auth -> auth

                        // 1. PUBLIC: Health Check & Auth Login (Endpoint Auth phải public)
                        // Giả sử Auth Service có endpoint login/register
                        .requestMatchers("/actuator/health", "/api/auth/**").permitAll()

                        // 2. PUBLIC: GET Reviews List
                        // Cho phép GET /api/review/reviewee/{UUID}
                        .requestMatchers(HttpMethod.GET, "/api/review/reviewee/*").permitAll()

                        // 3. PUBLIC: GET Rating Summary
                        // Cho phép GET /api/review/reviewee/{UUID}/rating
                        .requestMatchers(HttpMethod.GET, "/api/review/reviewee/*/rating").permitAll()
                        // POST /api/review yêu cầu authentication (ROLE_CUSTOMER/ROLE_DRIVER)
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        config.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        config.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
        config.setAllowCredentials(allowCredentials);
        config.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
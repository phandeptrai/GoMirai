package com.gomirai.user.config;

import com.gomirai.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.gomirai.common.security.JwtAuthenticationEntryPoint;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Enable @PreAuthorize annotation
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

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, JwtAuthenticationEntryPoint authEntryPoint) {
        this.jwtFilter = jwtFilter;
        this.authEntryPoint = authEntryPoint;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled for REST API with JWT
                .csrf(csrf -> csrf.disable())

                // Stateless session
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Exception handling
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))

                // Authorization
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll() // Only health endpoint
                        .requestMatchers("/api/users/*/public").permitAll() // Public user info for other services
                        .anyRequest().authenticated())

                // JWT filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        // ✅ Security headers
        http.headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss -> {
                }) // XSS Protection deprecated in Spring Security 6.1+
        );

        return http.build();
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();

        // ✅ Read from application.properties - UserService chỉ cho phép ApiGateway
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

        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
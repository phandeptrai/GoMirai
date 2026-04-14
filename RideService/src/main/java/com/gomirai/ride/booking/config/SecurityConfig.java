package com.gomirai.ride.booking.config;

import com.gomirai.common.security.GatewayDelegationAuthenticationFilter;
import com.gomirai.common.security.InternalApiKeyFilter;
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

    @Value("${security.internal.api-key}")
    private String internalApiKey;

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
    public InternalApiKeyFilter internalApiKeyFilter() {
        return new InternalApiKeyFilter(internalApiKey);
    }

    @Bean
    public GatewayDelegationAuthenticationFilter gatewayDelegationAuthenticationFilter() {
        return new GatewayDelegationAuthenticationFilter(internalApiKey);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/actuator/prometheus").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Map and Pricing
                        .requestMatchers("/api/map/directions").permitAll()
                        .requestMatchers("/api/pricing/estimate").permitAll()
                        .requestMatchers("/api/pricing/debug/**").permitAll()
                        // Internal endpoints: sử dụng hasAuthority để tránh nhầm lẫn prefix 'ROLE_'
                        .requestMatchers(HttpMethod.GET,  "/api/booking/*/info").hasAuthority("ROLE_INTERNAL_SERVICE")
                        .requestMatchers(HttpMethod.POST, "/api/booking/*/cancel-no-driver").hasAuthority("ROLE_INTERNAL_SERVICE")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayDelegationAuthenticationFilter(), JwtAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter(), GatewayDelegationAuthenticationFilter.class);

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

package com.gomirai.notification.config;

import com.gomirai.common.security.GatewayDelegationAuthenticationFilter;
import com.gomirai.common.security.InternalApiKeyFilter;
import com.gomirai.common.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public GatewayDelegationAuthenticationFilter gatewayDelegationAuthenticationFilter() {
        return new GatewayDelegationAuthenticationFilter(internalApiKey);
    }

    @Bean
    public InternalApiKeyFilter internalApiKeyFilter() {
        return new InternalApiKeyFilter(internalApiKey);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ws/**", "/actuator/**").permitAll() // Allow WebSocket handshake
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(gatewayDelegationAuthenticationFilter(), JwtAuthenticationFilter.class)
            .addFilterBefore(internalApiKeyFilter(), GatewayDelegationAuthenticationFilter.class)
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .build();
    }
}

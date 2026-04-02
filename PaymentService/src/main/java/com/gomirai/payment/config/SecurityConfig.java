package com.gomirai.payment.config;

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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${security.internal.api-key}")
    private String internalApiKey;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
        http
                // Vô hiệu hóa CSRF vì dùng Stateless JWT
                .csrf(csrf -> csrf.disable())

                // Cấu hình Session Stateless
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Cho phép kiểm tra sức khỏe hệ thống
                        .requestMatchers("/actuator/health").permitAll()

                        // ➡️ QUAN TRỌNG: Cho phép BookingService gọi các API thanh toán/hoàn tiền nội
                        // bộ
                        // Trong thực tế, nên check Role INTERNAL_SERVICE hoặc IP whitelist
                        .requestMatchers("/api/payment/internal/**").permitAll()

                        // ➡️ VNPay callback endpoints - PHẢI PUBLIC vì VNPay server gọi trực tiếp
                        // và user được redirect từ VNPay về đây
                        .requestMatchers("/api/payment/vnpay/callback").permitAll()
                        .requestMatchers("/api/payment/vnpay/return").permitAll()

                        // Các API liên quan đến ví cá nhân yêu cầu phải có Token hợp lệ
                        .requestMatchers("/api/payment/**", "/api/payment/transactions/**").authenticated()

                        .anyRequest().authenticated())

                // Thêm Filter JWT từ common-lib để xác thực người dùng
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayDelegationAuthenticationFilter(), JwtAuthenticationFilter.class)
                .addFilterBefore(internalApiKeyFilter(), GatewayDelegationAuthenticationFilter.class);

        return http.build();
    }
}
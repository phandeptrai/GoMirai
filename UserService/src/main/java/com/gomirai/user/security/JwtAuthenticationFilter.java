package com.gomirai.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
    
        try {
            String header = request.getHeader("Authorization");
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                Optional<Claims> claimsOpt = jwtService.parseToken(token);
                
                if (claimsOpt.isPresent()) {
                    Claims claims = claimsOpt.get();
                    String userIdStr = claims.getSubject();
                    String role = claims.get("role", String.class);

                    if (userIdStr != null) {
                        try {
                            UUID userId = UUID.fromString(userIdStr);
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    role != null ? List.of(new SimpleGrantedAuthority("ROLE_" + role)) : Collections.emptyList()
                            );
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        } catch (IllegalArgumentException e) {
                            // Invalid UUID format - bỏ qua, không set authentication
                            log.warn("Invalid UUID format in token: {}", userIdStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log lỗi nhưng không throw để tránh 500
            // Request sẽ tiếp tục đi vào chain dưới dạng "Anonymous" (chưa đăng nhập)
            // Và sẽ bị chặn bởi SecurityConfig trả về 401 ở bước sau
            log.error("Không thể xác thực user từ token: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }
}
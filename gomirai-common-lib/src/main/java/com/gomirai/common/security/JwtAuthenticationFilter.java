package com.gomirai.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared JWT Authentication Filter for all GoMirai microservices
 * 
 * Extracts JWT from Authorization header, validates it,
 * and sets Spring Security context
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        
        try {
            // Extract Authorization header
            String header = request.getHeader(HttpHeaders.AUTHORIZATION);
            
            if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
                String token = header.substring(7);
                
                // Parse and validate token
                Optional<Claims> claimsOpt = jwtService.parseToken(token);
                
                if (claimsOpt.isPresent()) {
                    Claims claims = claimsOpt.get();
                    String userIdStr = claims.getSubject();
                    String role = claims.get("role", String.class);
                    
                    if (StringUtils.hasText(userIdStr) && StringUtils.hasText(role)) {
                        try {
                            UUID userId = UUID.fromString(userIdStr);
                            
                            // Create authentication token with role
                            UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                    userId,
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
                                );
                            
                            // Set authentication in Security Context
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            
                            log.debug("JWT authentication successful for user: {}, role: {}", userId, role);
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid UUID format in JWT token: {}", userIdStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log error but don't throw - let request continue as unauthenticated
            // SecurityConfig will handle returning 401 for protected endpoints
            log.error("JWT authentication error: {}", e.getMessage());
        }
        
        // Continue filter chain
        filterChain.doFilter(request, response);
    }
}



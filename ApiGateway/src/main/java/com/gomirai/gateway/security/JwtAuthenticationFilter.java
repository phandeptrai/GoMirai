package com.gomirai.gateway.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Optional<Claims> claims = jwtService.parseToken(token);
            
            if (claims.isPresent()) {
                Claims c = claims.get();
                String subject = c.getSubject();
                String role = c.get("role", String.class);
                
                if (StringUtils.hasText(subject) && StringUtils.hasText(role)) {
                    try {
                        UUID userId = UUID.fromString(subject);
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userId, 
                            null, 
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID format - không set authentication
                    }
                }
            }
        }
        
        filterChain.doFilter(request, response);
    }
}


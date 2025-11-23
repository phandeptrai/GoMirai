package com.gomirai.auth.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gomirai.auth.security.RateLimitingService;

/**
 * Rate Limiting Filter for Auth endpoints
 * Protects login and register endpoints from brute force attacks
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Only apply rate limiting to login and register endpoints
        if (shouldApplyRateLimit(path)) {
            String clientIp = getClientIP(request);
            
            if (!rateLimitingService.tryConsume(clientIp)) {
                // Rate limit exceeded
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                    "{\"error\":\"Too Many Requests\"," +
                    "\"message\":\"Rate limit exceeded. Please try again later.\"," +
                    "\"status\":429}"
                );
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private boolean shouldApplyRateLimit(String path) {
        return path.equals("/auth/login") || path.equals("/auth/register");
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}


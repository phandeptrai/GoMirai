package com.gomirai.common.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Shared JWT Authentication Entry Point for all GoMirai microservices
 * 
 * Handles authentication failures by returning 401 Unauthorized
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        // Return 401 Unauthorized for authentication failures
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}



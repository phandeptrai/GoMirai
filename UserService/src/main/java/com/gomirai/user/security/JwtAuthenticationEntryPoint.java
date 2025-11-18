package com.gomirai.user.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Trả về 401
        
         String json = String.format("{\"status\": 401, \"message\": \"Truy cập bị từ chối: Token không hợp lệ hoặc đã hết hạn\", \"timestamp\": \"%s\"}", java.time.LocalDateTime.now());
        
        response.getWriter().write(json);
    }
}
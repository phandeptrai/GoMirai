package com.gomirai.identity.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gomirai.identity.security.RateLimitingService;

/**
 * Filter giới hạn số lượng request (Rate Limiting).
 * 
 * Mục đích: Bảo vệ endpoints login và register khỏi tấn công brute force.
 * 
 * Cách hoạt động:
 * - Mỗi IP address có một "bucket" chứa tokens
 * - Mỗi request tiêu thụ 1 token
 * - Tokens được tự động nạp lại theo thời gian
 * - Khi hết tokens → trả về HTTP 429 (Too Many Requests)
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    /**
     * Xử lý mỗi request đi qua filter.
     * Filter này được đánh dấu OncePerRequestFilter để đảm bảo chỉ chạy 1
     * lần/request.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Chỉ áp dụng rate limiting cho endpoints login và register
        // Các endpoints khác không cần giới hạn nghiêm ngặt
        if (shouldApplyRateLimit(path)) {
            // Lấy IP của client (xử lý cả trường hợp qua proxy)
            String clientIp = getClientIP(request);

            // Thử tiêu thụ 1 token từ bucket của IP này
            if (!rateLimitingService.tryConsume(clientIp)) {
                // Hết tokens → từ chối request
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\"," +
                                "\"message\":\"Quá nhiều yêu cầu. Vui lòng thử lại sau.\"," +
                                "\"status\":429}");
                return; // Không tiếp tục filter chain
            }
        }

        // Còn tokens hoặc không cần rate limit → tiếp tục xử lý request
        filterChain.doFilter(request, response);
    }

    /**
     * Xác định endpoint nào cần áp dụng rate limiting.
     * Chỉ giới hạn login và register vì đây là mục tiêu của brute force.
     */
    private boolean shouldApplyRateLimit(String path) {
        return path.equals("/auth/login") || path.equals("/auth/register");
    }

    /**
     * Lấy IP thực của client.
     * 
     * Xử lý trường hợp request đi qua proxy/load balancer:
     * - Header X-Forwarded-For chứa IP gốc của client
     * - Nếu không có header → dùng remote address trực tiếp
     */
    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        // X-Forwarded-For có thể chứa nhiều IP (client, proxy1, proxy2, ...)
        // IP đầu tiên là IP gốc của client
        return xfHeader.split(",")[0];
    }
}

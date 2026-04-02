package com.gomirai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Tin cậy request đã được xác thực JWT tại API Gateway...
 */
@Slf4j
public class GatewayDelegationAuthenticationFilter extends OncePerRequestFilter {

    public static final String GATEWAY_USER_ID_HEADER = "X-Gateway-User-Id";
    public static final String GATEWAY_USER_ROLE_HEADER = "X-Gateway-User-Role";

    public GatewayDelegationAuthenticationFilter(String internalApiKey) {
        // Biến này không còn dùng trực tiếp ở đây nhưng giữ Constructor để không làm hỏng Code các service khác
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userIdStr = request.getHeader(GATEWAY_USER_ID_HEADER);
        String roleRaw = request.getHeader(GATEWAY_USER_ROLE_HEADER);

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();

        // Chỉ chấp nhận delegation nếu ĐÃ xác thực qua Internal API Key (INTERNAL_SERVICE_PRINCIPAL).
        // Điều này đảm bảo hacker không thể bypass Gateway bằng cách tự gửi X-Gateway-User-Id trực tiếp tới service.
        if (existing == null || !existing.isAuthenticated() || !InternalApiKeyFilter.INTERNAL_SERVICE_PRINCIPAL.equals(existing.getPrincipal())) {
            // Nếu đã là UUID (JWT hợp lệ hoặc đã delegation xong), cho qua
            if (existing != null && (existing.getPrincipal() instanceof UUID)) {
                filterChain.doFilter(request, response);
                return;
            }
            // Không phải internal service (hoặc sai key) -> không tin tưởng delegation headers
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(userIdStr) || !StringUtils.hasText(roleRaw)) {
            // Có internal key hợp lệ nhưng không có thông tin user -> là gọi giữa các service thuần túy
            filterChain.doFilter(request, response);
            return;
        }

        try {
            UUID userId = UUID.fromString(userIdStr.trim());
            String normalized = roleRaw.trim().toUpperCase();
            if (!normalized.equals("CUSTOMER") && !normalized.equals("DRIVER") && !normalized.equals("ADMIN")) {
                log.warn("Rejected gateway delegation: invalid role {}", roleRaw);
                filterChain.doFilter(request, response);
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + normalized)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Gateway-delegated auth: userId={}, role={}", userId, normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Rejected gateway delegation: bad userId {}", userIdStr);
        }

        filterChain.doFilter(request, response);
    }
}

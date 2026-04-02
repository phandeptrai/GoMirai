package com.gomirai.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Filter kiểm tra Internal API Key cho các endpoint nội bộ giữa các microservices.
 *
 * Cách hoạt động:
 *  - Request đến endpoint nội bộ (vd /api/booking/{id}/info) phải có header X-Internal-Api-Key
 *  - Nếu key khớp với cấu hình → set authentication = INTERNAL_SERVICE (permit)
 *  - Nếu key sai hoặc thiếu và endpoint yêu cầu internal auth → trả 401
 *
 * Cách dùng: khởi tạo bean này trong SecurityConfig của service cần bảo vệ,
 * truyền vào internalApiKey từ @Value("${security.internal.api-key}").
 */
@Slf4j
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    public static final String INTERNAL_SERVICE_PRINCIPAL = "INTERNAL_SERVICE";
    public static final String ROLE_INTERNAL = "ROLE_INTERNAL_SERVICE";

    private final String internalApiKey;

    public InternalApiKeyFilter(String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()) {
            // Nếu đã xác thực bằng User (UUID) hoặc đã là Internal Service, bỏ qua filter này
            if (existing.getPrincipal() instanceof UUID || INTERNAL_SERVICE_PRINCIPAL.equals(existing.getPrincipal())) {
                filterChain.doFilter(request, response);
                return;
            }
        }

        String keyFromHeader = request.getHeader(INTERNAL_API_KEY_HEADER);

        if (StringUtils.hasText(keyFromHeader) && keyFromHeader.equals(internalApiKey)) {
            // Key hợp lệ → set authentication là INTERNAL_SERVICE
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            INTERNAL_SERVICE_PRINCIPAL,
                            null,
                            List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Internal API Key authenticated for URI: {}", request.getRequestURI());
            // Quan trọng: Sau khi xác thực API Key thành công, ta tiếp tục chain
        } else if (StringUtils.hasText(keyFromHeader)) {
            log.warn("Invalid Internal API Key received for URI: {}", request.getRequestURI());
            // Key sai thì ta để các filter sau (như JWT) thử sức, hoặc SecurityConfig sẽ chặn 401/403 sau.
        }

        filterChain.doFilter(request, response);
    }
}

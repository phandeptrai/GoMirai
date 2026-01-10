package com.gomirai.auth.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response trả về sau khi đăng nhập/đăng ký thành công.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private UUID userId;
    private String role;
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * Constructor tiện lợi với 3 tham số (tokenType mặc định là "Bearer").
     */
    public AuthResponse(UUID userId, String role, String accessToken) {
        this.userId = userId;
        this.role = role;
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
    }
}

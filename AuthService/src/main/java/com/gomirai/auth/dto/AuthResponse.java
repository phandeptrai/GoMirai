package com.gomirai.auth.dto;

import java.util.UUID;

public class AuthResponse {
    private UUID userId;
    private String role;
    private String accessToken;
    private String tokenType = "Bearer";

    public AuthResponse() {}

    public AuthResponse(UUID userId, String role, String accessToken) {
        this.userId = userId;
        this.role = role;
        this.accessToken = accessToken;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
}




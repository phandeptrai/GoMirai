package com.gomirai.auth.dto;

import java.util.UUID;

public class TokenValidationResponse {
    private boolean valid;
    private UUID userId;
    private String role;

    public TokenValidationResponse() {}

    public TokenValidationResponse(boolean valid, UUID userId, String role) {
        this.valid = valid;
        this.userId = userId;
        this.role = role;
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}




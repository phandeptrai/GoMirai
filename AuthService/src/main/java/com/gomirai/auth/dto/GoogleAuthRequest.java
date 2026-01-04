package com.gomirai.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for Google OAuth authentication.
 * 
 * The client sends the Google ID token (obtained from Google Sign-In SDK)
 * to this endpoint for server-side verification.
 */
public class GoogleAuthRequest {

    /**
     * Google ID Token obtained from Google Sign-In on the client.
     * This token is verified server-side with Google's OAuth2 API.
     */
    @NotBlank(message = "Google ID token is required")
    private String idToken;

    public GoogleAuthRequest() {
    }

    public GoogleAuthRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }
}

package com.gomirai.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Google OAuth authentication.
 * 
 * The client sends the Google ID token (obtained from Google Sign-In SDK)
 * to this endpoint for server-side verification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {

    /**
     * Google ID Token obtained from Google Sign-In on the client.
     * This token is verified server-side with Google's OAuth2 API.
     */
    @NotBlank(message = "Google ID token is required")
    private String idToken;
}

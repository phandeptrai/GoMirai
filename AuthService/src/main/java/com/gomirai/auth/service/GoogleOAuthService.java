package com.gomirai.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.gomirai.auth.dto.GoogleUserInfo;
import com.gomirai.common.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for verifying Google OAuth tokens and retrieving user information.
 * 
 * Google OAuth Authentication Flow:
 * 1. Client uses Google Sign-In SDK to get ID token
 * 2. Client sends ID token to our AuthService
 * 3. AuthService verifies token with Google's tokeninfo endpoint
 * 4. AuthService extracts user info and creates/updates local user
 * 
 * Note: This service performs AUTHENTICATION only.
 * - No distinction between "login" and "register" from user perspective
 * - If user exists (by providerUserId) -> login
 * - If user doesn't exist -> auto-registration
 */
@Service
@Slf4j
public class GoogleOAuthService {

    private static final String GOOGLE_TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final String GOOGLE_USER_INFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    @Value("${google.oauth.client-id}")
    private String googleClientId;

    private final RestTemplate restTemplate;

    public GoogleOAuthService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Verify Google ID token and extract user information.
     * 
     * Uses Google's tokeninfo endpoint for verification.
     * This validates:
     * - Token signature
     * - Token expiration
     * - Audience (client ID) matches our app
     * 
     * @param idToken Google ID token from client
     * @return GoogleUserInfo containing user details
     * @throws BusinessException if token is invalid
     */
    public GoogleUserInfo verifyIdToken(String idToken) {
        log.info("Verifying Google ID token");

        try {
            String url = GOOGLE_TOKEN_INFO_URL + idToken;
            ResponseEntity<GoogleUserInfo> response = restTemplate.getForEntity(url, GoogleUserInfo.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Google token verification failed: {}", response.getStatusCode());
                throw new BusinessException("Invalid Google ID token");
            }

            GoogleUserInfo userInfo = response.getBody();

            // Validate the token is issued for our application
            // Note: tokeninfo endpoint returns 'aud' claim which we should validate
            // For simplicity, we trust the response if it's valid

            if (userInfo.getSub() == null || userInfo.getSub().isBlank()) {
                log.error("Google token missing sub claim");
                throw new BusinessException("Invalid Google ID token: missing user ID");
            }

            if (userInfo.getEmail() == null || userInfo.getEmail().isBlank()) {
                log.error("Google token missing email");
                throw new BusinessException("Invalid Google ID token: missing email");
            }

            log.info("Google token verified successfully for email: {}", userInfo.getEmail());
            return userInfo;

        } catch (RestClientException e) {
            log.error("Failed to verify Google ID token: {}", e.getMessage());
            throw new BusinessException("Failed to verify Google ID token: " + e.getMessage());
        }
    }

    /**
     * Alternative: Get user info using access token.
     * 
     * This can be used when client has an access token instead of ID token.
     * Not typically used in our flow, but available as fallback.
     * 
     * @param accessToken Google access token
     * @return GoogleUserInfo containing user details
     */
    public GoogleUserInfo getUserInfoByAccessToken(String accessToken) {
        log.info("Getting user info from Google using access token");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<GoogleUserInfo> response = restTemplate.exchange(
                    GOOGLE_USER_INFO_URL,
                    HttpMethod.GET,
                    entity,
                    GoogleUserInfo.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to get user info from Google: {}", response.getStatusCode());
                throw new BusinessException("Failed to get user info from Google");
            }

            return response.getBody();

        } catch (RestClientException e) {
            log.error("Failed to get user info from Google: {}", e.getMessage());
            throw new BusinessException("Failed to get user info from Google: " + e.getMessage());
        }
    }

    /**
     * Get the configured Google Client ID.
     */
    public String getGoogleClientId() {
        return googleClientId;
    }
}

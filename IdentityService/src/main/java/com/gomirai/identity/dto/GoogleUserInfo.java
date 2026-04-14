package com.gomirai.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing user information from Google OAuth.
 * 
 * This is parsed from the Google ID token after verification.
 * Reference:
 * https://developers.google.com/identity/protocols/oauth2/openid-connect
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleUserInfo {

    /**
     * Unique identifier for the Google account (Google "sub" claim).
     * This is used as providerUserId in our system.
     */
    private String sub;

    /**
     * Email address from Google account.
     */
    private String email;

    /**
     * Whether the email has been verified by Google.
     */
    @JsonProperty("email_verified")
    private boolean emailVerified;

    /**
     * Full name from Google account.
     */
    private String name;

    /**
     * Profile picture URL from Google account.
     */
    private String picture;

    /**
     * Given name (first name) from Google account.
     */
    @JsonProperty("given_name")
    private String givenName;

    /**
     * Family name (last name) from Google account.
     */
    @JsonProperty("family_name")
    private String familyName;

    /**
     * Locale/language preference.
     */
    private String locale;
}

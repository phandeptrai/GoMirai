package com.gomirai.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO containing user information from Google OAuth.
 * 
 * This is parsed from the Google ID token after verification.
 * Reference:
 * https://developers.google.com/identity/protocols/oauth2/openid-connect
 */
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

    // Getters and Setters

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPicture() {
        return picture;
    }

    public void setPicture(String picture) {
        this.picture = picture;
    }

    public String getGivenName() {
        return givenName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    @Override
    public String toString() {
        return "GoogleUserInfo{" +
                "sub='" + sub + '\'' +
                ", email='" + email + '\'' +
                ", emailVerified=" + emailVerified +
                ", name='" + name + '\'' +
                '}';
    }
}

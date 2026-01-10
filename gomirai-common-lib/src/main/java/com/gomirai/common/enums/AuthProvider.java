package com.gomirai.common.enums;

/**
 * Authentication Providers supported by GoMirai
 */
public enum AuthProvider {
    /**
     * Local authentication (phone + password)
     */
    LOCAL,
    
    /**
     * Google OAuth authentication
     */
    GOOGLE,
    
    /**
     * Facebook OAuth authentication
     */
    FACEBOOK,
    
    /**
     * Apple authentication
     */
    APPLE;

    /**
     * Check if provider is OAuth-based
     */
    public boolean isOAuth() {
        return this == GOOGLE || this == FACEBOOK || this == APPLE;
    }

    /**
     * Check if provider is local
     */
    public boolean isLocal() {
        return this == LOCAL;
    }
}



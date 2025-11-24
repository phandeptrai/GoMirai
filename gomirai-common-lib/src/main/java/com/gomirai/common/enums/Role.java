package com.gomirai.common.enums;

/**
 * User Roles in GoMirai system
 */
public enum Role {
    /**
     * Regular customer who books rides
     */
    CUSTOMER,
    
    /**
     * Driver who provides ride services
     */
    DRIVER,
    
    /**
     * System administrator with full access
     */
    ADMIN;

    /**
     * Get role name with ROLE_ prefix for Spring Security
     */
    public String getAuthority() {
        return "ROLE_" + this.name();
    }

    /**
     * Check if role is admin
     */
    public boolean isAdmin() {
        return this == ADMIN;
    }

    /**
     * Check if role is driver
     */
    public boolean isDriver() {
        return this == DRIVER;
    }

    /**
     * Check if role is customer
     */
    public boolean isCustomer() {
        return this == CUSTOMER;
    }
}



package com.gomirai.common.enums;

/**
 * Service names in GoMirai microservices architecture
 * Used for service discovery and routing
 */
public enum ServiceName {
    API_GATEWAY("ApiGateway", "api-gateway"),
    AUTH_SERVICE("AuthService", "auth-service"),
    USER_SERVICE("UserService", "user-service"),
    PAYMENT_SERVICE("PaymentService", "payment-service"),
    RIDE_SERVICE("RideService", "ride-service"),
    NOTIFICATION_SERVICE("NotificationService", "notification-service");

    private final String consulName;
    private final String internalName;

    ServiceName(String consulName, String internalName) {
        this.consulName = consulName;
        this.internalName = internalName;
    }

    public String getConsulName() {
        return consulName;
    }

    public String getInternalName() {
        return internalName;
    }

    /**
     * Get service name by Consul name
     */
    public static ServiceName fromConsulName(String consulName) {
        for (ServiceName service : values()) {
            if (service.consulName.equalsIgnoreCase(consulName)) {
                return service;
            }
        }
        throw new IllegalArgumentException("Unknown service: " + consulName);
    }
}



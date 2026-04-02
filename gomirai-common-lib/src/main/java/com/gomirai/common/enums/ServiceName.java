package com.gomirai.common.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * SINGLE SOURCE OF TRUTH for GoMirai service routing.
 *
 * <p>Each constant encodes three orthogonal pieces of information:
 * <ul>
 *   <li>{@code consulName}  – name registered in Consul (must match {@code spring.application.name})</li>
 *   <li>{@code pathId}      – singular URL segment used by the API Gateway  (/api/{pathId}/**)</li>
 *   <li>{@code pathPrefix}  – exact path prefix forwarded to the downstream service</li>
 * </ul>
 *
 * <h3>Routing contract</h3>
 * <pre>
 *   Client URL          Gateway resolves      Downstream receives
 *   ──────────────────────────────────────────────────────────────
 *   /api/auth/login  →  auth-service       →  /auth/login
 *   /api/user/me     →  user-service       →  /api/user/me
 *   /api/driver/me   →  driver-service     →  /api/driver/me
 *   /api/booking/1   →  booking-service    →  /api/booking/1
 *   /api/payment/…   →  payment-service    →  /api/payment/…
 *   /api/tracking/…  →  tracking-service   →  /api/tracking/…
 *   /api/pricing/…   →  pricing-service    →  /api/pricing/…
 *   /api/map/…       →  map-service        →  /api/map/…
 *   /api/review/…    →  review-service     →  /api/review/…
 *   /api/notification/… → notification-service → /api/notification/…
 * </pre>
 *
 * <h3>Backward-compatibility aliases (plural → singular)</h3>
 * The Gateway normalizes incoming plural path segments before lookup so that
 * existing clients using {@code /api/drivers}, {@code /api/users}, etc. continue
 * to work without any client-side changes.
 */
public enum ServiceName {

    AUTH_SERVICE        ("auth-service",         "auth",         "/auth"),
    USER_SERVICE        ("user-service",          "user",         "/api/user"),
    DRIVER_SERVICE      ("driver-service",        "driver",       "/api/driver"),
    BOOKING_SERVICE     ("booking-service",       "booking",      "/api/booking"),
    PAYMENT_SERVICE     ("payment-service",       "payment",      "/api/payment"),
    TRACKING_SERVICE    ("tracking-service",      "tracking",     "/api/tracking"),
    PRICING_SERVICE     ("pricing-service",       "pricing",      "/api/pricing"),
    MAP_SERVICE         ("map-service",           "map",          "/api/map"),
    REVIEW_SERVICE      ("review-service",        "review",       "/api/review"),
    NOTIFICATION_SERVICE("notification-service",  "notification", "/api/notification");

    // ──────────────────────────────────────────────────────────────────────────
    // Fields
    // ──────────────────────────────────────────────────────────────────────────

    /** Name registered in Consul. Must match {@code spring.application.name}. */
    private final String consulName;

    /** Singular URL path segment for the API Gateway: /api/{pathId}/**  */
    private final String pathId;

    /**
     * Path prefix forwarded verbatim to the downstream service.
     * AuthService is the only service that uses a non-/api prefix (/auth).
     */
    private final String pathPrefix;

    // ──────────────────────────────────────────────────────────────────────────
    // Plural → singular alias table (backward compatibility)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Map of plural URL aliases to their canonical singular pathIds.
     * Kept in one place so that adding a new service never requires
     * touching ProxyController or any other class.
     */
    private static final Map<String, String> PLURAL_ALIASES = Map.of(
        "users",         "user",
        "drivers",       "driver",
        "bookings",      "booking",
        "payments",      "payment",
        "reviews",       "review",
        "notifications", "notification",
        "trackings",     "tracking",
        "maps",          "map"
    );

    // ──────────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────────

    ServiceName(String consulName, String pathId, String pathPrefix) {
        this.consulName = consulName;
        this.pathId     = pathId;
        this.pathPrefix = pathPrefix;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Accessors
    // ──────────────────────────────────────────────────────────────────────────

    /** @return Consul service name, e.g. {@code "driver-service"} */
    public String getConsulName() { return consulName; }

    /** @return Gateway URL segment, e.g. {@code "driver"} */
    public String getPathId() { return pathId; }

    /** @return Internal path prefix, e.g. {@code "/api/driver"} */
    public String getPathPrefix() { return pathPrefix; }

    // ──────────────────────────────────────────────────────────────────────────
    // Factory methods
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resolve a URL path segment (the {@code {serviceId}} captured from
     * {@code /api/{serviceId}/**}) to a {@link ServiceName}.
     *
     * <p>Plural forms are automatically normalized to their singular equivalents
     * so that {@code /api/drivers/me} and {@code /api/driver/me} both route to
     * {@link #DRIVER_SERVICE}.
     *
     * @param segment the raw path segment from the URL (case-insensitive)
     * @return the matching ServiceName
     * @throws IllegalArgumentException if no service is registered for the segment
     */
    public static ServiceName fromPath(String segment) {
        if (segment == null || segment.isBlank()) {
            throw new IllegalArgumentException("Path segment must not be blank");
        }
        String normalized = normalizePlural(segment.toLowerCase().trim());
        return Arrays.stream(values())
                     .filter(s -> s.pathId.equals(normalized))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                         "No service registered for path segment: '" + segment + "'"));
    }

    /**
     * Attempt to resolve a URL path segment without throwing.
     *
     * @param segment the raw path segment
     * @return an Optional containing the {@link ServiceName}, or empty if unknown
     */
    public static Optional<ServiceName> tryFromPath(String segment) {
        if (segment == null || segment.isBlank()) return Optional.empty();
        String normalized = normalizePlural(segment.toLowerCase().trim());
        return Arrays.stream(values())
                     .filter(s -> s.pathId.equals(normalized))
                     .findFirst();
    }

    /**
     * Resolve a Consul service name to a {@link ServiceName}.
     *
     * @param name the consul name (e.g. {@code "driver-service"})
     * @return the matching ServiceName
     * @throws IllegalArgumentException if not found
     */
    public static ServiceName fromConsulName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Consul name must not be blank");
        }
        String lower = name.toLowerCase().trim();
        return Arrays.stream(values())
                     .filter(s -> s.consulName.equals(lower))
                     .findFirst()
                     .orElseThrow(() -> new IllegalArgumentException(
                         "No service registered for consul name: '" + name + "'"));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Normalize a plural path segment to its canonical singular form.
     * Only segments explicitly listed in {@link #PLURAL_ALIASES} are affected;
     * everything else is returned unchanged.
     */
    private static String normalizePlural(String segment) {
        return PLURAL_ALIASES.getOrDefault(segment, segment);
    }
}

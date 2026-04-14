package com.gomirai.ride.booking.client.cache;

import com.gomirai.ride.booking.dto.external.GeoPoint;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CacheKeyUtils {
    private CacheKeyUtils() {
    }

    /**
     * Round coordinates to 4 decimals (~11m) to increase cache hit rate.
     */
    public static String round4(Double value) {
        if (value == null) {
            return "null";
        }
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    public static String routeKey(GeoPoint origin, GeoPoint destination, String profile) {
        String oLat = origin != null ? round4(origin.getLatitude()) : "null";
        String oLng = origin != null ? round4(origin.getLongitude()) : "null";
        String dLat = destination != null ? round4(destination.getLatitude()) : "null";
        String dLng = destination != null ? round4(destination.getLongitude()) : "null";
        return String.join(":", "v1", "route", profile != null ? profile : "default", oLat, oLng, dLat, dLng);
    }

    public static String pricingKey(String vehicleType, double distanceKm, int durationMinutes, String region) {
        // Distance rounding reduces key cardinality without impacting pricing materially.
        String dist = BigDecimal.valueOf(distanceKm)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return String.join(":", "v1", "pricing",
                vehicleType != null ? vehicleType : "null",
                region != null ? region : "null",
                dist,
                String.valueOf(durationMinutes));
    }
}


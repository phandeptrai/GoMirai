package com.gomirai.ride.booking.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;

/**
 * Snapshot of a geographic address captured at booking creation time.
 *
 * <h3>PERF IMPROVEMENT #2 — Geo-Spatial Index</h3>
 * <p>A {@link GeoJsonPoint} field is stored alongside latitude/longitude so that
 * MongoDB's {@code 2dsphere} index (declared in {@code BookingGeoIndexConfig})
 * can accelerate nearby-driver queries via {@code $nearSphere} instead of
 * fetching all PENDING bookings and computing Haversine distances in the JVM.
 *
 * <p><b>Before:</b> O(N) full collection scan + Haversine in Java (CPU-bound).<br>
 * <b>After:</b> O(log N) index seek in MongoDB (I/O-bound, off-JVM).
 *
 * <p>The {@code point} field is automatically populated by
 * {@link com.gomirai.ride.booking.service.BookingService#normalizeLocation} when a
 * booking is created, so existing code paths that set {@code latitude} and
 * {@code longitude} do not need to be changed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddressSnapshot {
    private String fullAddress;

    @NotNull(message = "Latitude is required")
    @Min(value = -90, message = "Latitude must be between -90 and 90")
    @Max(value = 90, message = "Latitude must be between -90 and 90")
    private Double latitude;

    @NotNull(message = "Longitude is required")
    @Min(value = -180, message = "Longitude must be between -180 and 180")
    @Max(value = 180, message = "Longitude must be between -180 and 180")
    private Double longitude;

    /**
     * GeoJSON representation of this address used by the 2dsphere index.
     * Stored as {@code { type: "Point", coordinates: [lon, lat] }} in MongoDB
     * (GeoJSON convention: longitude first).
     *
     * <p>Set automatically when latitude/longitude are provided; never null for
     * valid bookings.
     */
    private GeoJsonPoint point;

    /**
     * Convenience constructor — builds the GeoJsonPoint automatically.
     */
    public AddressSnapshot(String fullAddress, Double latitude, Double longitude) {
        this.fullAddress = fullAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        if (latitude != null && longitude != null) {
            this.point = new GeoJsonPoint(longitude, latitude); // GeoJSON: lon, lat
        }
    }

    /**
     * Ensure the {@code point} field stays in sync with {@code latitude}/{@code longitude}.
     * Call this after any setter-based construction.
     */
    public void syncPoint() {
        if (this.latitude != null && this.longitude != null) {
            this.point = new GeoJsonPoint(this.longitude, this.latitude);
        }
    }
}

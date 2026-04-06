package com.gomirai.booking.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Ensures all MongoDB indexes required by BookingService exist at startup.
 *
 * <h3>PERF IMPROVEMENT #2 — 2dsphere index for geo-spatial driver search</h3>
 *
 * <p><b>Before:</b> O(N) Haversine computed in JVM over up to 50 fetched bookings.<br>
 * <b>After:</b> MongoDB {@code $nearSphere} + 2dsphere index → O(log N) seek in DB engine.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
// ensureIndex is deprecated in Spring Data MongoDB 4.5 but still functional — suppress at call sites
public class BookingIndexConfig {

    private final MongoTemplate mongoTemplate;

    @PostConstruct
    public void ensureIndexes() {
        try {
            IndexOperations ops = mongoTemplate.indexOps("bookings");

            // ── 2dsphere index on pickup location ──────────────────────────────
            // Enables $nearSphere queries used in getPendingBookingsForDriver.
            // createIndex() is the non-deprecated replacement for ensureIndex() in
            // Spring Data MongoDB 4.5+. Both are idempotent (no-op if index exists).
            ops.createIndex(new GeospatialIndex("pickupLocation.point")
                    .typed(GeoSpatialIndexType.GEO_2DSPHERE)
                    .named("idx_pickup_2dsphere"));
            log.info("✓ BookingIndex: 2dsphere index on pickupLocation.point ensured");

            // ── Compound index: (status, createdAt DESC) ───────────────────────
            ops.createIndex(new CompoundIndexDefinition(
                    new Document("status", 1).append("createdAt", -1))
                    .named("idx_status_createdAt"));
            log.info("✓ BookingIndex: compound (status, createdAt) index ensured");

            // ── Compound index: (status, vehicleType, createdAt DESC) ──────────
            ops.createIndex(new CompoundIndexDefinition(
                    new Document("status", 1).append("vehicleType", 1).append("createdAt", -1))
                    .named("idx_status_vehicleType_createdAt"));
            log.info("✓ BookingIndex: compound (status, vehicleType, createdAt) index ensured");

        } catch (Exception e) {
            // Non-fatal: log and continue — Atlas may need the Atlas Search index manager role.
            log.error("✗ Failed to ensure booking indexes (service still starts): {}", e.getMessage(), e);
        }
    }
}

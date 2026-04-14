package com.gomirai.ride.booking.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeospatialIndex;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ensures all MongoDB indexes required by BookingService exist after context startup.
 *
 * <h3>Why EventListener instead of @PostConstruct</h3>
 * <p>@PostConstruct runs <em>during</em> bean initialization — before the ApplicationContext is
 * fully started. If MongoDB is momentarily busy (e.g., recovering from a previous stress test with
 * 600+ connections), the {@link MongoTemplate} factory method itself can throw
 * {@code DataAccessResourceFailureException: Timeout while receiving message}.
 *
 * <p>Moving to {@link ContextRefreshedEvent} means:
 * <ul>
 *   <li>MongoTemplate is already initialized and the connection pool is warm.</li>
 *   <li>The application can accept health-check probes before index creation starts.</li>
 *   <li>A MongoDB transient blip at startup is isolated to index creation (non-fatal, logged).</li>
 * </ul>
 *
 * <h3>PERF IMPROVEMENT #2 — 2dsphere index for geo-spatial driver search</h3>
 * <p><b>Before:</b> O(N) Haversine computed in JVM over up to 50 fetched bookings.<br>
 * <b>After:</b> MongoDB {@code $nearSphere} + 2dsphere index → O(log N) seek in DB engine.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class BookingIndexConfig {

    private final MongoTemplate mongoTemplate;

    /**
     * Create/ensure indexes after the full ApplicationContext has refreshed.
     *
     * <p>Uses {@link ContextRefreshedEvent} (fired once after all beans are ready) instead of
     * {@code @PostConstruct} so that a transient MongoDB timeout during bean initialization
     * (serverSelectionTimeout hit while pool is spinning up) cannot crash the context loading.
     *
     * <p>{@code @Order(1)} ensures this runs early among context-refresh listeners.
     * All errors are caught and logged as WARNING — the service starts regardless.
     */
    @EventListener(ContextRefreshedEvent.class)
    @Order(1)
    public void ensureIndexes() {
        log.info("Ensuring MongoDB booking indexes (post-context-refresh)...");
        try {
            IndexOperations ops = mongoTemplate.indexOps("bookings");

            // ── Compound Geospatial Index (status, vehicleType, pickupLocation.point 2dsphere) ─────────
            // MongoDB can use this index to pre-filter by status + vehicleType before geo distance calc.
            // This replaces the separate geo + status indexes for the hot-path query.
            ops.createIndex(new CompoundIndexDefinition(
                    new Document("status", 1)
                            .append("vehicleType", 1)
                            .append("pickupLocation.point", "2dsphere"))
                    .named("idx_status_vehicleType_pickup_2dsphere"));
            log.info("✓ BookingIndex: compound geospatial (status, vehicleType, pickupLocation.point) ensured");

            ops.createIndex(new CompoundIndexDefinition(
                    new Document("status", 1).append("createdAt", -1))
                    .named("idx_status_createdAt"));
            log.info("✓ BookingIndex: compound (status, createdAt) index ensured");

            // ── Compound indices for Customer History (PERF FIX) ──────────────
            ops.createIndex(new CompoundIndexDefinition(
                    new Document("customerId", 1).append("status", 1).append("createdAt", -1))
                    .named("idx_customerId_status_createdAt"));
            ops.createIndex(new CompoundIndexDefinition(
                    new Document("customerId", 1).append("createdAt", -1))
                    .named("idx_customerId_createdAt"));
            log.info("✓ BookingIndex: compound customer-specific indices ensured");

            // ── Unique idempotencyKey (partial) ───────────────────────────────
            // Sparse-unique still indexes explicit nulls as one bucket → breaks with many legacy nulls.
            // Partial: only non-empty strings enter the index. Cannot use $ne:null in partialFilterExpression
            // (MongoDB rejects it as unsupported $not). Use $exists + $type string + $gt "" instead.
            dropIndexIfPresent(ops, "idempotencyKey");
            dropIndexIfPresent(ops, "uidx_idempotencyKey");
            Document idempotencyPartial = new Document("idempotencyKey", new Document("$exists", true)
                    .append("$type", "string")
                    .append("$gt", ""));
            ops.createIndex(new Index()
                    .on("idempotencyKey", Sort.Direction.ASC)
                    .unique()
                    .partial(PartialIndexFilter.of(idempotencyPartial))
                    .named("uidx_idempotencyKey"));
            log.info("✓ BookingIndex: unique partial index on idempotencyKey ensured");

            log.info("✓ All booking MongoDB indexes ensured successfully");

        } catch (Exception e) {
            // Non-fatal: log and continue — service is healthy even if index creation fails.
            // Indexes can be created manually via Atlas UI or mongo shell if needed.
            log.warn("✗ Failed to ensure booking indexes (service still starts): {}", e.getMessage());
        }
    }

    private static void dropIndexIfPresent(IndexOperations ops, String indexName) {
        try {
            ops.dropIndex(indexName);
            log.info("Dropped existing index '{}' before recreate", indexName);
        } catch (Exception e) {
            log.debug("No index to drop or drop skipped: {} — {}", indexName, e.getMessage());
        }
    }
}

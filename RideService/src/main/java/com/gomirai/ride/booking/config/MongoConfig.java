package com.gomirai.ride.booking.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Strict Mongo client timeouts for DB SLA class; see docs/SLA-ARCHITECTURE.md.
 *
 * <h3>Timeout rationale</h3>
 * <ul>
 *   <li><b>serverSelectionTimeout=30s</b>: Time for the driver to find a healthy Mongo primary.
 *       3s was too short — at startup with 600-connection pool, MongoDB may take 10–20s to accept
 *       all connections, causing MongoTemplate instantiation to fail before the app is even ready.
 *       30s gives the replica set election / recovery window without permanently blocking requests.</li>
 *   <li><b>readTimeout=5s</b>: Per-socket op budget (up from 3s). Index creation at startup can be
 *       slightly slower than regular queries.</li>
 *   <li><b>connectTimeout=2s</b>: TCP connect budget — unchanged, this is a network-level concern.</li>
 *   <li><b>maxWaitTime=5s</b>: How long a thread waits for a pool connection. 3s was hitting under
 *       600-VU spike; 5s gives headroom without starving callers.</li>
 * </ul>
 */
@Configuration
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer gomiraiMongoSlaTimeouts() {
        return builder -> builder
                .applyToClusterSettings(s -> s
                        // 10s: Cân bằng giữa startup latency và fail-fast under load.
                        // Tránh việc treo request đến 30s (max latency) nếu DB có "hiccup".
                        .serverSelectionTimeout(10, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(2, TimeUnit.SECONDS)
                        // 15s: Headroom cho các tác vụ index hoặc query phức tạp
                        .readTimeout(15, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(s -> s
                        // 10s: Thời gian tối đa để lấy một connection từ pool
                        .maxWaitTime(10, TimeUnit.SECONDS));
    }
}

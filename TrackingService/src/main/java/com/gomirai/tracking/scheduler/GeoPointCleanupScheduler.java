package com.gomirai.tracking.scheduler;

import com.gomirai.tracking.service.TrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to cleanup expired Geo Points from Redis.
 * 
 * Since Redis Geo Set doesn't support TTL directly, we need to manually
 * remove Geo Points whose metadata has expired (TTL = 5 minutes).
 * 
 * This task runs every 2 minutes to prevent memory leak.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeoPointCleanupScheduler {

    private final TrackingService trackingService;

    /**
     * Cleanup expired Geo Points every 2 minutes (configurable).
     * 
     * Fixed delay: runs after previous execution completes
     * Initial delay: 60 seconds (1 minute) after service startup
     * 
     * Default: 120000ms (2 minutes)
     */
    @Scheduled(fixedDelayString = "${tracking.cleanup.interval.ms:120000}", initialDelay = 60000)
    public void cleanupExpiredGeoPoints() {
        log.debug("Running scheduled cleanup for expired Geo Points...");
        trackingService.cleanupExpiredGeoPointsScheduled();
    }
}


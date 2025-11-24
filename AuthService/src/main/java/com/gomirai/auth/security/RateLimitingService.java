package com.gomirai.auth.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

/**
 * Rate Limiting Service using Bucket4j
 * Implements Token Bucket algorithm to prevent brute force attacks
 */
@Service
public class RateLimitingService {

    // Cache buckets per IP address
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Resolve bucket per IP address
     * Rate: 5 requests per minute for login/register endpoints
     */
    public Bucket resolveBucket(String key) {
        return cache.computeIfAbsent(key, k -> createNewBucket());
    }

    private Bucket createNewBucket() {
        // Allow 100 requests per minute (refill 100 tokens every 60 seconds)
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Try to consume 1 token from the bucket
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String key) {
        Bucket bucket = resolveBucket(key);
        return bucket.tryConsume(1);
    }

    /**
     * Get available tokens for debugging
     */
    public long getAvailableTokens(String key) {
        return resolveBucket(key).getAvailableTokens();
    }
}


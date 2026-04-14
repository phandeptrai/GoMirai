package com.gomirai.ride.booking.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CaffeineCacheConfig {

    @Bean(name = "shortLivedCaffeineCacheManager")
    public CaffeineCacheManager shortLivedCaffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("driver_location");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .maximumSize(200_000));
        return manager;
    }
}


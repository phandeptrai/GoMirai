package com.gomirai.driver.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MongoSlaConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer gomiraiMongoSlaTimeouts() {
        return builder -> builder
                .applyToClusterSettings(s -> s.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(s -> s
                        .maxWaitTime(3, TimeUnit.SECONDS)
                        .maxSize(15)           // default 100 → 15 (1-2 replicas, 15 conns/pod đủ)
                        .minSize(2)            // keep-alive 2 connections để tránh cold connect
                        .maxConnectionIdleTime(30, TimeUnit.SECONDS)
                        .maxConnectionLifeTime(120, TimeUnit.SECONDS));
    }
}

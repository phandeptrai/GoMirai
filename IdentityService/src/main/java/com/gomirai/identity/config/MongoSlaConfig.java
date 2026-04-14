package com.gomirai.identity.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fail-fast Mongo driver settings (server selection / socket / pool wait) for global HTTP SLA stack.
 */
@Configuration
public class MongoSlaConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer gomiraiMongoSlaTimeouts() {
        return builder -> builder
                .applyToClusterSettings(s -> s.serverSelectionTimeout(3, TimeUnit.SECONDS))
                .applyToSocketSettings(s -> s
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(3, TimeUnit.SECONDS))
                .applyToConnectionPoolSettings(s -> s.maxWaitTime(3, TimeUnit.SECONDS));
    }
}

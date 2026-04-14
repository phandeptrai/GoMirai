package com.gomirai.payment.config;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.gomirai.payment.repository")
public class MongoConfig {

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
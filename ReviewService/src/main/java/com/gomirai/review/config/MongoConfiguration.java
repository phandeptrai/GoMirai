package com.gomirai.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import java.util.Arrays;

@Configuration
public class MongoConfiguration {
    // Đăng ký Converters cho UUID
    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new ObjectIdToUuidConverter(),
                new UuidToObjectIdConverter()));
    }
}
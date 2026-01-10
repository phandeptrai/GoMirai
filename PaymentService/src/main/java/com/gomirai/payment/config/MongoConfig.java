package com.gomirai.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "com.gomirai.payment.repository")
public class MongoConfig {
    /*
     * Spring Boot 3.x tự động cấu hình UUID conversion.
     * Việc sử dụng @Field(targetType = FieldType.STRING) trong Model
     * là đủ để đảm bảo tính tương thích.
     */
}
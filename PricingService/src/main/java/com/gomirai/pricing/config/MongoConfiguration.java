package com.gomirai.pricing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.core.convert.converter.Converter;
import java.util.Arrays;
import java.util.List;

@Configuration
public class MongoConfiguration {

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = Arrays.asList(
                new ObjectIdToUuidConverter(),
                new UuidToObjectIdConverter());
        return new MongoCustomConversions(converters);
    }
}
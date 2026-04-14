package com.gomirai.ride.pricing.config;

import java.util.UUID;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import java.util.Arrays;
import java.util.List;

@Configuration
public class MongoConfiguration {

    @WritingConverter
    enum UuidToStringConverter implements Converter<UUID, String> {
        INSTANCE;

        @Override
        public String convert(UUID source) {
            return source == null ? null : source.toString();
        }
    }

    @ReadingConverter
    enum StringToUuidConverter implements Converter<String, UUID> {
        INSTANCE;

        @Override
        public UUID convert(String source) {
            return source == null ? null : UUID.fromString(source);
        }
    }

    @Bean
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = Arrays.asList(
                // Backward compatibility: some collections may still have ObjectId _id
                // while the model uses UUID.
                new ObjectIdToUuidConverter(),
                // Store UUIDs as plain strings to avoid binary UUID representation mismatches
                UuidToStringConverter.INSTANCE,
                StringToUuidConverter.INSTANCE);
        return new MongoCustomConversions(converters);
    }
}
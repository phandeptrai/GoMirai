package com.gomirai.tracking.config;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Test configuration that provides a mock RedisConnectionFactory.
 * This is needed because CI environment does not have a Redis instance.
 * The @Primary annotation ensures this bean takes precedence over the
 * auto-configured one.
 */
@TestConfiguration
public class TestRedisConfig {

    @Bean
    @Primary
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }
}

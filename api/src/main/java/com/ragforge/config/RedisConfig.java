package com.ragforge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration.
 *
 * We use StringRedisTemplate (not the generic RedisTemplate) because
 * Redis Streams work with string key-value pairs. This avoids the
 * default JDK serializer which produces unreadable binary data in Redis.
 */
@Configuration
public class RedisConfig {

    /**
     * StringRedisTemplate uses StringRedisSerializer for both keys and values,
     * making the data human-readable in Redis CLI (useful for debugging).
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}

package com.ragforge.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Load the Lua script once at startup
        this.script = new DefaultRedisScript<>();
        this.script.setLocation(new ClassPathResource("scripts/rate_limiter.lua"));
        this.script.setResultType(List.class);
    }

    /**
     * Checks if the request is allowed.
     * @throws RateLimitException if limit is exceeded, containing Retry-After.
     */
    public void consumeOrThrow(Long apiKeyId, int limitPerMinute) {
        String key = "ratelimit:apikey:" + apiKeyId;
        long now = Instant.now().getEpochSecond();
        
        // Execute Lua script atomically
        // ARGV: [capacity, current_time, requested_tokens]
        @SuppressWarnings("unchecked")
        List<Long> result = redisTemplate.execute(
            script, 
            List.of(key), 
            String.valueOf(limitPerMinute), 
            String.valueOf(now), 
            "1"
        );

        if (result == null || result.size() != 2) {
            throw new RuntimeException("Unexpected result from rate limit script");
        }

        long allowed = result.get(0);
        long retryAfter = result.get(1);

        if (allowed == 0) {
            throw new RateLimitException(retryAfter);
        }
    }
}

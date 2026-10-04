package com.ragforge.health;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom health endpoint that verifies actual connectivity to Postgres and Redis.
 * Spring Actuator's /actuator/health does similar checks, but this gives us a
 * clean JSON response at a memorable URL and lets us verify infra in Phase 1.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthController(JdbcTemplate jdbc, RedisConnectionFactory redisConnectionFactory) {
        this.jdbc = jdbc;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("postgres", checkPostgres());
        result.put("redis", checkRedis());
        return result;
    }

    private Map<String, Object> checkPostgres() {
        Map<String, Object> pg = new LinkedHashMap<>();
        try {
            // Simple query that also confirms pgvector extension is loaded
            String version = jdbc.queryForObject("SELECT version()", String.class);
            Boolean pgvectorInstalled = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                Boolean.class
            );
            pg.put("status", "UP");
            pg.put("version", version);
            pg.put("pgvector", Boolean.TRUE.equals(pgvectorInstalled) ? "installed" : "missing");
        } catch (Exception e) {
            pg.put("status", "DOWN");
            pg.put("error", e.getMessage());
        }
        return pg;
    }

    private Map<String, Object> checkRedis() {
        Map<String, Object> redis = new LinkedHashMap<>();
        try {
            // PING is the standard Redis liveness check
            String pong = redisConnectionFactory.getConnection().ping();
            redis.put("status", "UP");
            redis.put("ping", pong);
        } catch (Exception e) {
            redis.put("status", "DOWN");
            redis.put("error", e.getMessage());
        }
        return redis;
    }
}

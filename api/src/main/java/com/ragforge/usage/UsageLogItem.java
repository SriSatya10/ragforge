package com.ragforge.usage;

import java.time.Instant;

public record UsageLogItem(
        Long id,
        Long tenantId,
        Long apiKeyId,
        String endpoint,
        Integer latencyMs,
        Boolean cacheHit,
        Integer statusCode,
        Instant createdAt
) {
    public static UsageLogItem fromEntity(UsageLog log) {
        return new UsageLogItem(
                log.getId(),
                log.getTenantId(),
                log.getApiKeyId(),
                log.getEndpoint(),
                log.getLatencyMs(),
                log.getCacheHit(),
                log.getStatusCode(),
                log.getCreatedAt()
        );
    }
}

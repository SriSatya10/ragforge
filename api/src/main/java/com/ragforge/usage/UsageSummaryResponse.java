package com.ragforge.usage;

import java.util.Map;

public record UsageSummaryResponse(
        Long tenantId,
        long totalRequests,
        long totalCacheHits,
        double cacheHitRate,
        double averageLatencyMs,
        Map<String, Long> requestsByEndpoint
) {}

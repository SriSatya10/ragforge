package com.ragforge.usage;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class UsageService {

    private static final Logger log = LoggerFactory.getLogger(UsageService.class);

    private final UsageLogRepository usageLogRepository;
    private final MeterRegistry meterRegistry;

    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter queryTotalCounter;
    private final Timer queryLatencyTimer;

    public UsageService(UsageLogRepository usageLogRepository, MeterRegistry meterRegistry) {
        this.usageLogRepository = usageLogRepository;
        this.meterRegistry = meterRegistry;

        this.cacheHitCounter = Counter.builder("ragforge_query_cache_hits_total")
                .description("Total number of semantic cache hits")
                .register(meterRegistry);

        this.cacheMissCounter = Counter.builder("ragforge_query_cache_misses_total")
                .description("Total number of semantic cache misses")
                .register(meterRegistry);

        this.queryTotalCounter = Counter.builder("ragforge_queries_total")
                .description("Total queries executed")
                .register(meterRegistry);

        this.queryLatencyTimer = Timer.builder("ragforge_query_latency")
                .description("Query execution latency")
                .register(meterRegistry);
    }

    /**
     * Record request telemetry into usage_logs and Micrometer metrics.
     */
    @Transactional
    public void recordUsage(Long tenantId, Long apiKeyId, String endpoint, long latencyMs, boolean cacheHit, int statusCode) {
        try {
            UsageLog usageLog = new UsageLog(tenantId, apiKeyId, endpoint, (int) latencyMs, cacheHit, statusCode);
            usageLogRepository.save(usageLog);

            // Update Prometheus metrics
            queryTotalCounter.increment();
            queryLatencyTimer.record(latencyMs, TimeUnit.MILLISECONDS);

            if (cacheHit) {
                cacheHitCounter.increment();
            } else {
                cacheMissCounter.increment();
            }
        } catch (Exception e) {
            log.warn("Failed to record usage log for tenant {}: {}", tenantId, e.getMessage());
        }
    }

    /**
     * Get aggregate usage metrics for a specific tenant.
     */
    @Transactional(readOnly = true)
    public UsageSummaryResponse getSummary(Long tenantId) {
        long total = usageLogRepository.countByTenantId(tenantId);
        long hits = usageLogRepository.countByTenantIdAndCacheHitTrue(tenantId);
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        Double avgLatency = usageLogRepository.findAverageLatencyByTenantId(tenantId);

        Map<String, Long> endpointMap = new HashMap<>();
        List<Object[]> endpointCounts = usageLogRepository.countRequestsByEndpoint(tenantId);
        for (Object[] row : endpointCounts) {
            String endpoint = (String) row[0];
            Long count = (Long) row[1];
            endpointMap.put(endpoint, count);
        }

        return new UsageSummaryResponse(
                tenantId,
                total,
                hits,
                Math.round(hitRate * 1000.0) / 1000.0,
                avgLatency != null ? Math.round(avgLatency * 10.0) / 10.0 : 0.0,
                endpointMap
        );
    }

    /**
     * Get recent request logs for a tenant.
     */
    @Transactional(readOnly = true)
    public List<UsageLogItem> getRecentLogs(Long tenantId, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), 100);
        return usageLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, pageSize))
                .getContent()
                .stream()
                .map(UsageLogItem::fromEntity)
                .toList();
    }
}

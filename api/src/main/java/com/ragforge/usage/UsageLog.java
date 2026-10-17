package com.ragforge.usage;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for usage_logs table.
 * Tracks per-request telemetry for billing, auditing, and analytics.
 */
@Entity
@Table(name = "usage_logs")
public class UsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "api_key_id")
    private Long apiKeyId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "latency_ms", nullable = false)
    private Integer latencyMs;

    @Column(name = "cache_hit", nullable = false)
    private Boolean cacheHit = false;

    @Column(name = "status_code", nullable = false)
    private Integer statusCode = 200;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UsageLog() {}

    public UsageLog(Long tenantId, Long apiKeyId, String endpoint, Integer latencyMs, Boolean cacheHit, Integer statusCode) {
        this.tenantId = tenantId;
        this.apiKeyId = apiKeyId;
        this.endpoint = endpoint;
        this.latencyMs = latencyMs;
        this.cacheHit = cacheHit != null ? cacheHit : false;
        this.statusCode = statusCode != null ? statusCode : 200;
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public Boolean getCacheHit() { return cacheHit; }
    public void setCacheHit(Boolean cacheHit) { this.cacheHit = cacheHit; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

package com.ragforge.apikey.dto;

import java.time.Instant;

/**
 * Summary view for listing keys — never includes the raw key.
 */
public record ApiKeySummary(
    Long id,
    int rateLimitPerMin,
    boolean active,
    Instant createdAt
) {}

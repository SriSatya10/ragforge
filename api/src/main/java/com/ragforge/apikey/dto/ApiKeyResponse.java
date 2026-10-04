package com.ragforge.apikey.dto;

import java.time.Instant;

/**
 * Response after creating a new API key.
 * rawKey is returned ONCE here and never stored — the client must save it.
 */
public record ApiKeyResponse(
    Long id,
    String rawKey,       // only populated on creation
    int rateLimitPerMin,
    boolean active,
    Instant createdAt
) {}

package com.ragforge.auth.dto;

/**
 * Returned on successful register or login.
 * Contains the JWT token and tenant metadata.
 */
public record AuthResponse(
    String token,
    Long tenantId,
    String name,
    String email
) {}

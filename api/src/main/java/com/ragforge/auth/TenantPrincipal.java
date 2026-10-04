package com.ragforge.auth;

/**
 * Lightweight object stored in SecurityContext after JWT validation.
 * Controllers use this to get the authenticated tenant's ID without
 * needing to query the DB or parse the token again.
 *
 * Using a record for immutability and conciseness.
 */
public record TenantPrincipal(Long tenantId, String email) {
}

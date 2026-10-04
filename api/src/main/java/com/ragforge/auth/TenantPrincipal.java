package com.ragforge.auth;

/**
 * Lightweight object stored in SecurityContext after validation.
 * Controllers use this to get the authenticated tenant's ID without
 * needing to query the DB or parse the token again.
 *
 * apiKeyId is null if authenticated via JWT (e.g. Dashboard login), 
 * and non-null if authenticated via API Key.
 */
public record TenantPrincipal(Long tenantId, String email, Long apiKeyId) {
    public TenantPrincipal(Long tenantId, String email) {
        this(tenantId, email, null);
    }
}


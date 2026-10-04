package com.ragforge.apikey;

import com.ragforge.apikey.dto.ApiKeyResponse;
import com.ragforge.apikey.dto.ApiKeySummary;
import com.ragforge.auth.TenantPrincipal;
import com.ragforge.tenant.Tenant;
import com.ragforge.tenant.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * API key management: create and list keys for the authenticated tenant.
 *
 * Security flow:
 * 1. Generate a random UUID as the raw key
 * 2. SHA-256 hash it and store only the hash
 * 3. Return the raw key ONCE to the client
 *
 * This is the same pattern GitHub, Stripe, etc. use for API keys.
 */
@RestController
@RequestMapping("/keys")
public class ApiKeyController {

    private final ApiKeyRepository apiKeyRepo;
    private final TenantRepository tenantRepo;

    public ApiKeyController(ApiKeyRepository apiKeyRepo, TenantRepository tenantRepo) {
        this.apiKeyRepo = apiKeyRepo;
        this.tenantRepo = tenantRepo;
    }

    /** Create a new API key. The raw key is returned only in this response. */
    @PostMapping
    public ResponseEntity<ApiKeyResponse> createKey(@AuthenticationPrincipal TenantPrincipal principal) {
        Tenant tenant = tenantRepo.findById(principal.tenantId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        // Generate raw key and its hash
        String rawKey = "rf_" + UUID.randomUUID().toString().replace("-", "");
        String keyHash = sha256(rawKey);

        ApiKey apiKey = new ApiKey();
        apiKey.setTenant(tenant);
        apiKey.setKeyHash(keyHash);
        // rateLimitPerMin defaults to 60 (set in entity)

        apiKey = apiKeyRepo.save(apiKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiKeyResponse(
                        apiKey.getId(),
                        rawKey,   // returned once, never stored
                        apiKey.getRateLimitPerMin(),
                        apiKey.isActive(),
                        apiKey.getCreatedAt()));
    }

    /** List all keys for the authenticated tenant (without raw keys). */
    @GetMapping
    public List<ApiKeySummary> listKeys(@AuthenticationPrincipal TenantPrincipal principal) {
        return apiKeyRepo.findByTenantId(principal.tenantId()).stream()
                .map(k -> new ApiKeySummary(
                        k.getId(),
                        k.getRateLimitPerMin(),
                        k.isActive(),
                        k.getCreatedAt()))
                .toList();
    }

    /**
     * SHA-256 hash a string and return the hex digest.
     * Used to hash API keys before storage.
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec — this never happens
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

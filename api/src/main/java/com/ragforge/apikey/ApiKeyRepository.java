package com.ragforge.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    /** Look up a key by its hash — used during API key authentication in later phases. */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /** List all keys for a tenant — used by the /keys GET endpoint. */
    List<ApiKey> findByTenantId(Long tenantId);
}

package com.ragforge.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    /**
     * Find a document by id AND tenant_id.
     * CRITICAL: always filter by tenantId to enforce tenant isolation.
     * Never use findById alone — that would leak documents across tenants.
     */
    Optional<Document> findByIdAndTenantId(Long id, Long tenantId);

    /** List all documents for a tenant (for a future "list my documents" endpoint). */
    List<Document> findByTenantId(Long tenantId);
}

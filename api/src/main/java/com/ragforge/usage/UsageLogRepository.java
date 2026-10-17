package com.ragforge.usage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UsageLogRepository extends JpaRepository<UsageLog, Long> {

    Page<UsageLog> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    List<UsageLog> findTop50ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    long countByTenantId(Long tenantId);

    long countByTenantIdAndCacheHitTrue(Long tenantId);

    @Query("SELECT COALESCE(AVG(u.latencyMs), 0.0) FROM UsageLog u WHERE u.tenantId = :tenantId")
    Double findAverageLatencyByTenantId(@Param("tenantId") Long tenantId);

    @Query("SELECT u.endpoint, COUNT(u) FROM UsageLog u WHERE u.tenantId = :tenantId GROUP BY u.endpoint")
    List<Object[]> countRequestsByEndpoint(@Param("tenantId") Long tenantId);
}

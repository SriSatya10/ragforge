package com.ragforge.usage;

import com.ragforge.auth.TenantPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Phase 14: Tenant usage and analytics endpoint.
 * Allows tenants to inspect their request counts, cache hit rate,
 * average latency, and recent request history.
 */
@RestController
@RequestMapping("/usage")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    /**
     * Get aggregate usage metrics for the authenticated tenant.
     */
    @GetMapping
    public ResponseEntity<UsageSummaryResponse> getUsageSummary(
            @AuthenticationPrincipal TenantPrincipal principal) {
        UsageSummaryResponse summary = usageService.getSummary(principal.tenantId());
        return ResponseEntity.ok(summary);
    }

    /**
     * Get recent request log entries for the authenticated tenant.
     */
    @GetMapping("/logs")
    public ResponseEntity<List<UsageLogItem>> getRecentLogs(
            @AuthenticationPrincipal TenantPrincipal principal,
            @RequestParam(defaultValue = "20") int limit) {
        List<UsageLogItem> logs = usageService.getRecentLogs(principal.tenantId(), limit);
        return ResponseEntity.ok(logs);
    }
}

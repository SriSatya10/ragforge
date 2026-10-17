package com.ragforge;

import com.ragforge.auth.JwtService;
import com.ragforge.tenant.Tenant;
import com.ragforge.tenant.TenantRepository;
import com.ragforge.usage.UsageLog;
import com.ragforge.usage.UsageLogRepository;
import com.ragforge.usage.UsageSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 14: Integration tests for /usage endpoint and telemetry logging.
 */
class UsageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UsageLogRepository usageLogRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Tenant tenant;
    private String token;

    @BeforeEach
    void setUp() {
        usageLogRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = new Tenant();
        tenant.setName("Usage Test Corp");
        tenant.setEmail("admin@usagetest.com");
        tenant.setPasswordHash(passwordEncoder.encode("secret123"));
        tenant = tenantRepository.save(tenant);
        token = jwtService.generateToken(tenant.getId(), tenant.getEmail());
    }

    @Test
    @DisplayName("GET /usage returns empty summary when no requests logged")
    void getUsage_returnsEmptySummary() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UsageSummaryResponse> response = restTemplate.exchange(
                "/usage", HttpMethod.GET, entity, UsageSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().totalRequests());
        assertEquals(0, response.getBody().totalCacheHits());
        assertEquals(0.0, response.getBody().cacheHitRate());
    }

    @Test
    @DisplayName("GET /usage aggregates query telemetry correctly")
    void getUsage_aggregatesTelemetry() {
        // Insert sample usage records
        usageLogRepository.save(new UsageLog(tenant.getId(), null, "/query", 120, false, 200));
        usageLogRepository.save(new UsageLog(tenant.getId(), null, "/query", 45, true, 200));
        usageLogRepository.save(new UsageLog(tenant.getId(), null, "/documents", 80, false, 202));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UsageSummaryResponse> response = restTemplate.exchange(
                "/usage", HttpMethod.GET, entity, UsageSummaryResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UsageSummaryResponse summary = response.getBody();
        assertNotNull(summary);
        assertEquals(3, summary.totalRequests());
        assertEquals(1, summary.totalCacheHits());
        assertTrue(summary.cacheHitRate() > 0.3);
        assertTrue(summary.averageLatencyMs() > 0);
        assertEquals(2, summary.requestsByEndpoint().get("/query"));
        assertEquals(1, summary.requestsByEndpoint().get("/documents"));
    }

    @Test
    @DisplayName("GET /usage requires authentication")
    void getUsage_unauthenticated_returnsForbidden() {
        ResponseEntity<String> response = restTemplate.getForEntity("/usage", String.class);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}

package com.ragforge;

import com.ragforge.auth.JwtService;
import com.ragforge.tenant.Tenant;
import com.ragforge.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 12: Tenant Isolation Integration Tests.
 *
 * Verifies that:
 * - Tenant A cannot access Tenant B's documents
 * - Tenant A cannot see Tenant B's document status
 * - Unauthenticated requests are rejected
 * - Each tenant's data is completely isolated
 */
class TenantIsolationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String tokenTenantA;
    private String tokenTenantB;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();

        // Create Tenant A
        Tenant tenantA = new Tenant();
        tenantA.setName("Company A");
        tenantA.setEmail("admin@company-a.com");
        tenantA.setPasswordHash(passwordEncoder.encode("password-a"));
        tenantA = tenantRepository.save(tenantA);
        tokenTenantA = jwtService.generateToken(tenantA.getId(), tenantA.getEmail());

        // Create Tenant B
        Tenant tenantB = new Tenant();
        tenantB.setName("Company B");
        tenantB.setEmail("admin@company-b.com");
        tenantB.setPasswordHash(passwordEncoder.encode("password-b"));
        tenantB = tenantRepository.save(tenantB);
        tokenTenantB = jwtService.generateToken(tenantB.getId(), tenantB.getEmail());
    }

    @Test
    @DisplayName("Tenant A cannot see Tenant B's documents (returns 404)")
    void tenantA_cannotAccessTenantB_documents() {
        // Step 1: Tenant A uploads a document
        Long docId = uploadDocumentAs(tokenTenantA, "tenant-a-secret.txt", "Secret data for company A only.");
        assertNotNull(docId, "Document upload should return an ID");

        // Step 2: Tenant A can see their own document
        ResponseEntity<String> ownDoc = getDocumentStatus(tokenTenantA, docId);
        assertEquals(HttpStatus.OK, ownDoc.getStatusCode(),
                "Tenant A should see their own document");

        // Step 3: Tenant B tries to access Tenant A's document — should get 404
        ResponseEntity<String> crossDoc = getDocumentStatus(tokenTenantB, docId);
        assertEquals(HttpStatus.NOT_FOUND, crossDoc.getStatusCode(),
                "Tenant B should NOT see Tenant A's document");
    }

    @Test
    @DisplayName("Unauthenticated request to documents endpoint returns 403")
    void unauthenticatedRequest_isForbidden() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/documents/1", HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode(),
                "Unauthenticated requests should be rejected with 403");
    }

    @Test
    @DisplayName("Two tenants can independently upload documents with same filename")
    void twoTenants_canUploadSameFilename() {
        Long docIdA = uploadDocumentAs(tokenTenantA, "report.txt", "Company A quarterly report.");
        Long docIdB = uploadDocumentAs(tokenTenantB, "report.txt", "Company B quarterly report.");

        assertNotNull(docIdA);
        assertNotNull(docIdB);
        assertNotEquals(docIdA, docIdB, "Documents should have different IDs");

        // Each tenant can only see their own
        assertEquals(HttpStatus.OK, getDocumentStatus(tokenTenantA, docIdA).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, getDocumentStatus(tokenTenantA, docIdB).getStatusCode());

        assertEquals(HttpStatus.OK, getDocumentStatus(tokenTenantB, docIdB).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, getDocumentStatus(tokenTenantB, docIdA).getStatusCode());
    }

    @Test
    @DisplayName("Registration creates isolated tenant with unique namespace")
    void registration_createsIsolatedTenant() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String body = """
                {"name": "New Corp", "email": "new@newcorp.com", "password": "securepass123"}
                """;
        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Registration should return 201 CREATED");
        assertTrue(response.getBody().contains("token"),
                "Response should contain a JWT token");
    }

    // --- Helper methods ---

    private Long uploadDocumentAs(String token, String filename, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity("/documents", requestEntity, String.class);

        if (response.getStatusCode() == HttpStatus.ACCEPTED && response.getBody() != null) {
            // Extract documentId from JSON response (simple parsing)
            String responseBody = response.getBody();
            int idStart = responseBody.indexOf("\"documentId\":") + 13;
            int idEnd = responseBody.indexOf(",", idStart);
            return Long.parseLong(responseBody.substring(idStart, idEnd).trim());
        }
        return null;
    }

    private ResponseEntity<String> getDocumentStatus(String token, Long docId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange("/documents/" + docId, HttpMethod.GET, entity, String.class);
    }
}

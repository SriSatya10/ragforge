package com.ragforge;

import com.ragforge.auth.JwtService;
import com.ragforge.auth.Tenant;
import com.ragforge.auth.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String jwtToken;

    @BeforeEach
    void setUp() {
        tenantRepository.deleteAll();
        
        Tenant tenant = new Tenant();
        tenant.setEmail("test@ragforge.com");
        tenant.setPasswordHash(passwordEncoder.encode("password"));
        tenantRepository.save(tenant);

        jwtToken = jwtService.generateToken(tenant.getId(), tenant.getEmail());
    }

    @Test
    void testUploadDocument_Success() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // Mocking a file upload by passing a String instead of a real file for simplicity in this test
        body.add("file", new org.springframework.core.io.ByteArrayResource("Dummy PDF content".getBytes()) {
            @Override
            public String getFilename() {
                return "test.pdf";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/documents", requestEntity, String.class);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    }

    @Test
    void testUploadDocument_WithoutAuth_Fails() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource("Dummy content".getBytes()) {
            @Override
            public String getFilename() {
                return "test.pdf";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity("/documents", requestEntity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}

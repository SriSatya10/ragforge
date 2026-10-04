package com.ragforge.query;

import com.ragforge.query.dto.Citation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private VectorSearchService vectorSearchService;

    @BeforeEach
    void setUp() {
        vectorSearchService = new VectorSearchService(jdbcTemplate);
    }

    @Test
    void testSearch_EnsuresTenantIsolation() {
        // Arrange
        Long expectedTenantId = 42L;
        List<Double> queryEmbedding = List.of(0.1, 0.2, 0.3);
        int topK = 5;

        when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Act
        vectorSearchService.search(expectedTenantId, queryEmbedding, topK);

        // Assert
        // We want to verify that the query was called with the correct tenantId parameter
        ArgumentCaptor<Object> vectorCaptor1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> tenantIdCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> vectorCaptor2 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> topKCaptor = ArgumentCaptor.forClass(Object.class);

        verify(jdbcTemplate).query(
                any(String.class),
                any(RowMapper.class),
                vectorCaptor1.capture(),
                tenantIdCaptor.capture(),
                vectorCaptor2.capture(),
                topKCaptor.capture()
        );

        // Verify that the second parameter passed to the SQL is exactly the tenant ID
        assertEquals(expectedTenantId, tenantIdCaptor.getValue(), "Tenant ID must be passed to the SQL query to ensure isolation.");
    }
}

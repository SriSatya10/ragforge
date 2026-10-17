package com.ragforge.query;

import com.ragforge.auth.TenantPrincipal;
import com.ragforge.cache.SemanticCacheService;
import com.ragforge.ingestion.EmbeddingClient;
import com.ragforge.llm.LlmService;
import com.ragforge.query.dto.QueryRequest;
import com.ragforge.query.dto.QueryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueryControllerTest {

    @Mock private HybridSearchOrchestrator hybridSearchOrchestrator;
    @Mock private LlmService llmService;
    @Mock private SemanticCacheService semanticCacheService;
    @Mock private EmbeddingClient embeddingClient;
    @Mock private com.ragforge.usage.UsageService usageService;

    private QueryController queryController;

    @BeforeEach
    void setUp() {
        queryController = new QueryController(
                hybridSearchOrchestrator, llmService, semanticCacheService, embeddingClient, usageService);
    }

    @Test
    void testQuery_CacheHit_BypassesLLM() {
        // Arrange
        TenantPrincipal principal = new TenantPrincipal(1L, "test@example.com");
        QueryRequest req = new QueryRequest("What is RAG?", 5);
        List<Double> embedding = List.of(0.1, 0.2);
        
        when(embeddingClient.embed(List.of(req.question()))).thenReturn(List.of(embedding));
        when(semanticCacheService.checkCache(principal.tenantId(), embedding)).thenReturn("Cached Answer");

        // Act
        QueryResponse response = queryController.query(req, principal);

        // Assert
        assertTrue(response.cacheHit());
        assertEquals("Cached Answer", response.answer());
        
        // Ensure we bypassed the slow pipeline
        verify(hybridSearchOrchestrator, never()).search(anyLong(), anyString(), anyInt());
        verify(llmService, never()).generateAnswer(anyString(), anyList());
    }
}

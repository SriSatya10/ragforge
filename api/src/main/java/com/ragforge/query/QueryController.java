package com.ragforge.query;

import com.ragforge.auth.TenantPrincipal;
import com.ragforge.ingestion.EmbeddingClient;
import com.ragforge.query.dto.Citation;
import com.ragforge.query.dto.QueryRequest;
import com.ragforge.query.dto.QueryResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Handles RAG queries from clients.
 *
 * Phase 6 implementation: Basic vector search.
 * 1. Embed the user's question.
 * 2. Search for the most similar chunks.
 * 3. Return a mock answer (LLM integration comes in Phase 7).
 */
@RestController
@RequestMapping("/query")
public class QueryController {

    private final EmbeddingClient embeddingClient;
    private final VectorSearchService vectorSearchService;

    public QueryController(EmbeddingClient embeddingClient,
                           VectorSearchService vectorSearchService) {
        this.embeddingClient = embeddingClient;
        this.vectorSearchService = vectorSearchService;
    }

    @PostMapping
    public QueryResponse query(
            @Valid @RequestBody QueryRequest req,
            @AuthenticationPrincipal TenantPrincipal principal) {

        // 1. Embed the question
        List<List<Double>> embeddings = embeddingClient.embed(List.of(req.question()));
        List<Double> queryEmbedding = embeddings.get(0);

        // 2. Perform vector search in PostgreSQL
        List<Citation> citations = vectorSearchService.search(
                principal.tenantId(),
                queryEmbedding,
                req.topK()
        );

        // 3. (Placeholder for Phase 7 LLM call)
        String mockAnswer = "This is a placeholder answer. In Phase 7, the LLM will generate a real answer using the retrieved citations.";

        return new QueryResponse(mockAnswer, citations, false);
    }
}

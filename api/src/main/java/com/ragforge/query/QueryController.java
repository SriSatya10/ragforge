package com.ragforge.query;

import com.ragforge.auth.TenantPrincipal;
import com.ragforge.ingestion.EmbeddingClient;
import com.ragforge.llm.LlmService;
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
 */
@RestController
@RequestMapping("/query")
public class QueryController {

    private final EmbeddingClient embeddingClient;
    private final VectorSearchService vectorSearchService;
    private final LlmService llmService;

    public QueryController(EmbeddingClient embeddingClient,
                           VectorSearchService vectorSearchService,
                           LlmService llmService) {
        this.embeddingClient = embeddingClient;
        this.vectorSearchService = vectorSearchService;
        this.llmService = llmService;
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

        // 3. Generate answer using LLM
        String answer = llmService.generateAnswer(req.question(), citations);

        return new QueryResponse(answer, citations, false);
    }
}

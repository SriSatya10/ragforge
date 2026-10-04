package com.ragforge.query;

import com.ragforge.auth.TenantPrincipal;
import com.ragforge.cache.SemanticCacheService;
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

    private final HybridSearchOrchestrator hybridSearchOrchestrator;
    private final LlmService llmService;
    private final SemanticCacheService semanticCacheService;
    private final EmbeddingClient embeddingClient;

    public QueryController(HybridSearchOrchestrator hybridSearchOrchestrator,
                           LlmService llmService,
                           SemanticCacheService semanticCacheService,
                           EmbeddingClient embeddingClient) {
        this.hybridSearchOrchestrator = hybridSearchOrchestrator;
        this.llmService = llmService;
        this.semanticCacheService = semanticCacheService;
        this.embeddingClient = embeddingClient;
    }

    @PostMapping
    public QueryResponse query(
            @Valid @RequestBody QueryRequest req,
            @AuthenticationPrincipal TenantPrincipal principal) {

        // 1. Embed the question
        List<Double> queryEmbedding = embeddingClient.embed(List.of(req.question())).get(0);

        // 2. Check Semantic Cache
        String cachedAnswer = semanticCacheService.checkCache(principal.tenantId(), queryEmbedding);
        if (cachedAnswer != null) {
            // Return cached answer with empty citations for now (cache hit)
            return new QueryResponse(cachedAnswer, List.of(), true);
        }

        // 3. Perform Hybrid Search
        List<Citation> citations = hybridSearchOrchestrator.search(
                principal.tenantId(),
                req.question(),
                req.topK()
        );

        // 4. Generate answer using LLM
        String answer = llmService.generateAnswer(req.question(), citations);

        // 5. Save to Cache
        semanticCacheService.saveCache(principal.tenantId(), req.question(), queryEmbedding, answer);

        return new QueryResponse(answer, citations, false);
    }
}



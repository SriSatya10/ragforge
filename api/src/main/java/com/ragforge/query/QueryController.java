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
    private final com.ragforge.usage.UsageService usageService;

    public QueryController(HybridSearchOrchestrator hybridSearchOrchestrator,
                           LlmService llmService,
                           SemanticCacheService semanticCacheService,
                           EmbeddingClient embeddingClient,
                           com.ragforge.usage.UsageService usageService) {
        this.hybridSearchOrchestrator = hybridSearchOrchestrator;
        this.llmService = llmService;
        this.semanticCacheService = semanticCacheService;
        this.embeddingClient = embeddingClient;
        this.usageService = usageService;
    }

    @PostMapping
    public QueryResponse query(
            @Valid @RequestBody QueryRequest req,
            @AuthenticationPrincipal TenantPrincipal principal) {

        long startTime = System.currentTimeMillis();

        // 1. Embed the question
        List<Double> queryEmbedding = embeddingClient.embed(List.of(req.question())).get(0);

        // 2. Check Semantic Cache
        String cachedAnswer = semanticCacheService.checkCache(principal.tenantId(), queryEmbedding);
        if (cachedAnswer != null) {
            long latency = System.currentTimeMillis() - startTime;
            usageService.recordUsage(principal.tenantId(), principal.apiKeyId(), "/query", latency, true, 200);
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

        // 5. Save to Cache (only if response was successfully generated)
        if (answer != null && !answer.startsWith("Error")) {
            semanticCacheService.saveCache(principal.tenantId(), req.question(), queryEmbedding, answer);
        }

        long latency = System.currentTimeMillis() - startTime;
        usageService.recordUsage(principal.tenantId(), principal.apiKeyId(), "/query", latency, false, 200);

        return new QueryResponse(answer, citations, false);
    }
}



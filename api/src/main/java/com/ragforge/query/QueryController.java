package com.ragforge.query;

import com.ragforge.auth.TenantPrincipal;
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

    public QueryController(HybridSearchOrchestrator hybridSearchOrchestrator,
                           LlmService llmService) {
        this.hybridSearchOrchestrator = hybridSearchOrchestrator;
        this.llmService = llmService;
    }

    @PostMapping
    public QueryResponse query(
            @Valid @RequestBody QueryRequest req,
            @AuthenticationPrincipal TenantPrincipal principal) {

        // 1 & 2. Perform Hybrid Search (Vector + Keyword) with Reciprocal Rank Fusion and Reranking
        List<Citation> citations = hybridSearchOrchestrator.search(
                principal.tenantId(),
                req.question(),
                req.topK()
        );

        // 3. Generate answer using LLM
        String answer = llmService.generateAnswer(req.question(), citations);

        return new QueryResponse(answer, citations, false);
    }
}


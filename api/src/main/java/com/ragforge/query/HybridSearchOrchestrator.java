package com.ragforge.query;

import com.ragforge.ingestion.EmbeddingClient;
import com.ragforge.query.dto.Citation;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates Hybrid Search (Vector + Keyword) using Reciprocal Rank Fusion (RRF),
 * followed by cross-encoder reranking.
 */
@Service
public class HybridSearchOrchestrator {

    private final VectorSearchService vectorSearchService;
    private final KeywordSearchService keywordSearchService;
    private final EmbeddingClient embeddingClient;
    private final RerankClient rerankClient;

    // Standard RRF constant
    private static final int RRF_K = 60;

    public HybridSearchOrchestrator(VectorSearchService vectorSearchService,
                                    KeywordSearchService keywordSearchService,
                                    EmbeddingClient embeddingClient,
                                    RerankClient rerankClient) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.embeddingClient = embeddingClient;
        this.rerankClient = rerankClient;
    }

    public List<Citation> search(Long tenantId, String question, int finalTopK) {
        // We fetch more candidates from the DB before filtering/reranking
        int fetchK = Math.max(finalTopK * 4, 20);

        // 1. Vector Search
        List<Double> queryEmbedding = embeddingClient.embed(List.of(question)).get(0);
        List<Citation> vectorResults = vectorSearchService.search(tenantId, queryEmbedding, fetchK);

        // 2. Keyword Search
        List<Citation> keywordResults = keywordSearchService.search(tenantId, question, fetchK);

        // 3. Reciprocal Rank Fusion (RRF)
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, Citation> citationMap = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            Citation c = vectorResults.get(i);
            rrfScores.put(c.content(), 1.0 / (RRF_K + i + 1));
            citationMap.putIfAbsent(c.content(), c);
        }

        for (int i = 0; i < keywordResults.size(); i++) {
            Citation c = keywordResults.get(i);
            double currentScore = rrfScores.getOrDefault(c.content(), 0.0);
            rrfScores.put(c.content(), currentScore + (1.0 / (RRF_K + i + 1)));
            citationMap.putIfAbsent(c.content(), c);
        }

        // Sort by RRF score descending and take top N for reranking
        List<Citation> rrfTopCandidates = rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(fetchK)
                .map(e -> citationMap.get(e.getKey()))
                .toList();

        if (rrfTopCandidates.isEmpty()) {
            return List.of();
        }

        // 4. Cross-Encoder Reranking
        List<String> documentsToRerank = rrfTopCandidates.stream()
                .map(Citation::content)
                .toList();

        List<Map<String, Object>> rerankedResults = rerankClient.rerank(question, documentsToRerank, finalTopK);

        // 5. Map back to citations
        return rerankedResults.stream()
                .map(res -> {
                    int originalIndex = (Integer) res.get("index");
                    double newScore = (Double) res.get("score");
                    Citation original = rrfTopCandidates.get(originalIndex);
                    // Create new Citation with updated score
                    return new Citation(original.documentId(), original.filename(), original.content(), newScore);
                })
                .toList();
    }
}

package com.ragforge.cache;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles Semantic Caching using PostgreSQL pgvector.
 * If a user asks a semantically similar question (e.g., > 0.95 cosine similarity),
 * we bypass the RAG pipeline and return the cached answer.
 */
@Service
public class SemanticCacheService {

    private final JdbcTemplate jdbc;

    // Threshold for similarity. 1 - 0.05 distance = 0.95 similarity.
    private static final double SIMILARITY_THRESHOLD = 0.95;

    public SemanticCacheService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Checks if a highly similar question was already answered for this tenant.
     * @return The cached answer, or null if no cache hit.
     */
    public String checkCache(Long tenantId, List<Double> queryEmbedding) {
        String vectorString = queryEmbedding.toString();

        String sql = """
            SELECT answer, 1 - (embedding <=> ?::vector) AS similarity
            FROM query_cache
            WHERE tenant_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT 1
            """;

        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                double similarity = rs.getDouble("similarity");
                if (similarity >= SIMILARITY_THRESHOLD) {
                    return rs.getString("answer");
                }
            }
            return null;
        }, vectorString, tenantId, vectorString);
    }

    /**
     * Saves a new question and its generated answer into the cache.
     */
    public void saveCache(Long tenantId, String question, List<Double> queryEmbedding, String answer) {
        String sql = "INSERT INTO query_cache (tenant_id, question, embedding, answer) VALUES (?, ?, ?::vector, ?)";
        jdbc.update(sql, tenantId, question, queryEmbedding.toString(), answer);
    }
}

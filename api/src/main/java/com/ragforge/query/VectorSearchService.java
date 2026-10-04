package com.ragforge.query;

import com.ragforge.query.dto.Citation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Performs vector similarity search in PostgreSQL using pgvector.
 */
@Service
public class VectorSearchService {

    private final JdbcTemplate jdbc;

    public VectorSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds the most similar chunks to the given embedding using cosine distance.
     * CRITICAL: The search is filtered by tenant_id to prevent data leaks.
     *
     * In pgvector, the <=> operator computes cosine distance.
     * Cosine similarity = 1 - cosine distance.
     */
    public List<Citation> search(Long tenantId, List<Double> queryEmbedding, int topK) {
        String vectorString = queryEmbedding.toString();

        // 1 - (embedding <=> query) gives cosine similarity where 1.0 is exact match
        String sql = """
            SELECT 
                c.content, 
                1 - (c.embedding <=> ?::vector) AS similarity,
                c.document_id,
                d.filename
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE c.tenant_id = ?
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?
            """;

        return jdbc.query(sql, (rs, rowNum) -> new Citation(
                rs.getLong("document_id"),
                rs.getString("filename"),
                rs.getString("content"),
                rs.getDouble("similarity")
        ), vectorString, tenantId, vectorString, topK);
    }
}

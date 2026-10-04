package com.ragforge.query;

import com.ragforge.query.dto.Citation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Performs full-text keyword search in PostgreSQL using the tsvector column.
 */
@Service
public class KeywordSearchService {

    private final JdbcTemplate jdbc;

    public KeywordSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Finds the most relevant chunks using PostgreSQL full-text search (ts_rank).
     * Filtered by tenant_id.
     */
    public List<Citation> search(Long tenantId, String query, int topK) {
        // websearch_to_tsquery allows Google-like syntax (quotes, OR, -)
        String sql = """
            SELECT 
                c.content, 
                ts_rank(c.tsv, websearch_to_tsquery('english', ?)) AS rank_score,
                c.document_id,
                d.filename
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE c.tenant_id = ?
              AND c.tsv @@ websearch_to_tsquery('english', ?)
            ORDER BY rank_score DESC
            LIMIT ?
            """;

        return jdbc.query(sql, (rs, rowNum) -> new Citation(
                rs.getLong("document_id"),
                rs.getString("filename"),
                rs.getString("content"),
                rs.getDouble("rank_score")
        ), query, tenantId, query, topK);
    }
}

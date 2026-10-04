package com.ragforge.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Saves chunks to the database.
 * We use JdbcTemplate (native SQL) instead of JPA because JPA/Hibernate
 * doesn't natively support Postgres vector types (pgvector) yet.
 */
@Repository
public class ChunkStore {

    private final JdbcTemplate jdbc;

    public ChunkStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Batch insert chunks.
     * The `tsv` column is auto-populated by the DB trigger we wrote in V1__init_schema.sql.
     */
    public void saveAll(Long documentId, Long tenantId, List<String> texts, List<List<Double>> embeddings) {
        if (texts.size() != embeddings.size()) {
            throw new IllegalArgumentException("texts and embeddings size mismatch");
        }

        String sql = "INSERT INTO chunks (document_id, tenant_id, content, embedding) VALUES (?, ?, ?, ?::vector)";

        jdbc.batchUpdate(sql, texts, embeddings.size(),
                (ps, text) -> {
                    int index = texts.indexOf(text);
                    List<Double> vector = embeddings.get(index);
                    
                    ps.setLong(1, documentId);
                    ps.setLong(2, tenantId);
                    ps.setString(3, text);
                    // pgvector expects a string like "[0.1, 0.2, ...]" for inserts
                    ps.setString(4, vector.toString());
                });
    }
}

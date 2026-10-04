-- V4: Add query_cache table for semantic caching
CREATE TABLE query_cache (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants(id),
    question TEXT NOT NULL,
    embedding vector(384) NOT NULL,
    answer TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster vector search on the cache
CREATE INDEX query_cache_embedding_idx ON query_cache USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

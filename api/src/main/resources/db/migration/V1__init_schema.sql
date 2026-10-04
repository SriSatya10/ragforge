-- ============================================================
-- V1: Core schema for multi-tenant RAG platform
-- ============================================================

-- pgvector extension — must be created before using the vector type
CREATE EXTENSION IF NOT EXISTS vector;

-- --------------------------------------------------------
-- Tenants: each company that signs up
-- --------------------------------------------------------
CREATE TABLE tenants (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- --------------------------------------------------------
-- API keys: hashed, per-tenant, with configurable rate limit
-- --------------------------------------------------------
CREATE TABLE api_keys (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    key_hash            VARCHAR(64)  NOT NULL UNIQUE,  -- SHA-256 hex
    rate_limit_per_min  INT          NOT NULL DEFAULT 60,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_api_keys_tenant ON api_keys(tenant_id);

-- --------------------------------------------------------
-- Documents: uploaded files tracked per tenant
-- --------------------------------------------------------
CREATE TABLE documents (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    filename    VARCHAR(512) NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_tenant ON documents(tenant_id);

-- --------------------------------------------------------
-- Chunks: text segments with embeddings and full-text search
-- vector(384) matches all-MiniLM-L6-v2 output dimensions
-- --------------------------------------------------------
CREATE TABLE chunks (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT       NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    content     TEXT         NOT NULL,
    tsv         TSVECTOR,                    -- populated by trigger below
    embedding   vector(384)                  -- populated during ingestion
);

-- tenant_id is denormalized from documents for fast filtered queries
-- (avoids a JOIN on every search — important at scale)
CREATE INDEX idx_chunks_tenant ON chunks(tenant_id);

-- HNSW index for approximate nearest neighbor search (cosine distance)
-- m=16 and ef_construction=200 balance recall vs build speed
CREATE INDEX idx_chunks_embedding ON chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- GIN index for full-text search on the tsvector column
CREATE INDEX idx_chunks_tsv ON chunks USING gin(tsv);

-- Auto-populate tsv column whenever content is inserted or updated
CREATE OR REPLACE FUNCTION chunks_tsv_trigger() RETURNS trigger AS $$
BEGIN
    NEW.tsv := to_tsvector('english', NEW.content);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_chunks_tsv
    BEFORE INSERT OR UPDATE OF content ON chunks
    FOR EACH ROW EXECUTE FUNCTION chunks_tsv_trigger();

-- --------------------------------------------------------
-- Usage logs: per-request telemetry for analytics + billing
-- --------------------------------------------------------
CREATE TABLE usage_logs (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    api_key_id  BIGINT       NOT NULL REFERENCES api_keys(id) ON DELETE CASCADE,
    endpoint    VARCHAR(255) NOT NULL,
    latency_ms  INT          NOT NULL,
    cache_hit   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_usage_logs_tenant ON usage_logs(tenant_id);
CREATE INDEX idx_usage_logs_key    ON usage_logs(api_key_id);

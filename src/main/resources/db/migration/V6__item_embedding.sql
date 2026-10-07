-- Semantic search: one embedding (a vector of numbers that captures the meaning of the text) per catalog item.
-- pgvector adds the "vector" column type and the distance operators; pg_trgm (V1) stays for the text side.
CREATE EXTENSION IF NOT EXISTS vector;

-- The size of the vector must match the embedding model (LLM_EMBEDDING_DIMENSIONS, default 768): Flyway replaces
-- the placeholder below. A different size later means dropping and recreating this table (it is only a cache of
-- what the model computes), see ADR 0012.
CREATE TABLE item_embedding (
    item_id     BIGINT PRIMARY KEY REFERENCES item (id) ON DELETE CASCADE,
    embedding   vector(${embeddingDimensions}) NOT NULL,
    -- which model produced it: embeddings of different models (or sizes) are not comparable
    model       VARCHAR(100) NOT NULL,
    dimensions  INTEGER      NOT NULL,
    -- SHA-256 of the text that was embedded: if the item changes, the hash changes and the row is stale
    source_hash CHAR(64)     NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- Approximate nearest neighbour search (HNSW) by COSINE distance. vector_cosine_ops must match the operator used
-- in the queries (<=>), otherwise PostgreSQL ignores the index.
CREATE INDEX idx_item_embedding_hnsw ON item_embedding USING hnsw (embedding vector_cosine_ops);

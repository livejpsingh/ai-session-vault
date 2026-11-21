-- V3: RAG / semantic-search tables (pgvector optional)
-- If the 'vector' extension is not installed on this PostgreSQL instance,
-- the session_chunks table is created WITHOUT the embedding column and
-- its HNSW index. Full-text search (pg_trgm + tsvector) still works.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Only attempt to load pgvector if it is available on this server
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_available_extensions WHERE name = 'vector'
    ) THEN
        CREATE EXTENSION IF NOT EXISTS vector;
    END IF;
END
$$;

-- RAG chunk table — embedding column present only when pgvector is loaded
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        EXECUTE '
            CREATE TABLE IF NOT EXISTS session_chunks (
                id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                session_id      UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
                chunk_index     INT         NOT NULL CHECK (chunk_index >= 0),
                role_sequence   VARCHAR(255) NOT NULL,
                content         TEXT        NOT NULL,
                token_count     INT         NOT NULL CHECK (token_count >= 0),
                metadata        JSONB       NOT NULL DEFAULT ''{}''::jsonb,
                embedding       vector(1536) NOT NULL,
                embedding_model VARCHAR(200) NOT NULL,
                content_hash    VARCHAR(64)  NOT NULL,
                created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                search_vector   TSVECTOR GENERATED ALWAYS AS (to_tsvector(''english'', coalesce(content, ''''))) STORED,
                CONSTRAINT uq_session_chunks_session_model_index
                    UNIQUE (session_id, embedding_model, chunk_index)
            )
        ';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_session_chunks_embedding
                 ON session_chunks USING hnsw (embedding vector_cosine_ops)
                 WITH (m = 16, ef_construction = 64)';
    ELSE
        -- pgvector not available: create table without embedding column
        CREATE TABLE IF NOT EXISTS session_chunks (
            id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
            session_id      UUID        NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
            chunk_index     INT         NOT NULL CHECK (chunk_index >= 0),
            role_sequence   VARCHAR(255) NOT NULL,
            content         TEXT        NOT NULL,
            token_count     INT         NOT NULL CHECK (token_count >= 0),
            metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
            embedding_model VARCHAR(200) NOT NULL,
            content_hash    VARCHAR(64)  NOT NULL,
            created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            search_vector   TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED,
            CONSTRAINT uq_session_chunks_session_model_index
                UNIQUE (session_id, embedding_model, chunk_index)
        );
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_session_chunks_search_vector
    ON session_chunks USING GIN (search_vector);

CREATE INDEX IF NOT EXISTS idx_session_chunks_metadata
    ON session_chunks USING GIN (metadata);

CREATE INDEX IF NOT EXISTS idx_session_chunks_session_model
    ON session_chunks (session_id, embedding_model);

-- RAG indexing job queue
CREATE TABLE IF NOT EXISTS rag_index_jobs (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      UUID        NOT NULL UNIQUE REFERENCES chat_sessions(id) ON DELETE CASCADE,
    embedding_model VARCHAR(200) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED')),
    attempts        INT         NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    generation      BIGINT      NOT NULL DEFAULT 1,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_until    TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_index_jobs_ready
    ON rag_index_jobs (next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

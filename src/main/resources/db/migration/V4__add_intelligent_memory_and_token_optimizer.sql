-- V4: Intelligent Memory Engine, Hierarchical Memory, and Token Optimization Platform

-- Memories table: Stores structured, categorized long-term, working, and episodic memories
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') THEN
        EXECUTE '
            CREATE TABLE IF NOT EXISTS memories (
                id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                tenant_id           VARCHAR(100) NOT NULL DEFAULT ''default'',
                project_id          VARCHAR(100),
                session_id          UUID REFERENCES chat_sessions(id) ON DELETE SET NULL,
                category            VARCHAR(50) NOT NULL,
                content             TEXT NOT NULL,
                short_summary       VARCHAR(500),
                importance_score    DOUBLE PRECISION NOT NULL DEFAULT 0.5,
                confidence_score    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
                embedding           vector(1536),
                source_message_ids  JSONB DEFAULT ''[]''::jsonb,
                metadata            JSONB DEFAULT ''{}''::jsonb,
                is_active           BOOLEAN NOT NULL DEFAULT true,
                valid_from          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                valid_until         TIMESTAMPTZ,
                created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                search_vector       TSVECTOR GENERATED ALWAYS AS (to_tsvector(''english'', coalesce(content, ''''))) STORED
            )
        ';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_memories_embedding
                 ON memories USING hnsw (embedding vector_cosine_ops)
                 WITH (m = 16, ef_construction = 64)';
    ELSE
        CREATE TABLE IF NOT EXISTS memories (
            id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
            project_id          VARCHAR(100),
            session_id          UUID REFERENCES chat_sessions(id) ON DELETE SET NULL,
            category            VARCHAR(50) NOT NULL,
            content             TEXT NOT NULL,
            short_summary       VARCHAR(500),
            importance_score    DOUBLE PRECISION NOT NULL DEFAULT 0.5,
            confidence_score    DOUBLE PRECISION NOT NULL DEFAULT 1.0,
            source_message_ids  JSONB DEFAULT '[]'::jsonb,
            metadata            JSONB DEFAULT '{}'::jsonb,
            is_active           BOOLEAN NOT NULL DEFAULT true,
            valid_from          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
            valid_until         TIMESTAMPTZ,
            created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
            search_vector       TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED
        );
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_memories_search_vector ON memories USING GIN (search_vector);
CREATE INDEX IF NOT EXISTS idx_memories_metadata ON memories USING GIN (metadata);
CREATE INDEX IF NOT EXISTS idx_memories_tenant_project ON memories (tenant_id, project_id);
CREATE INDEX IF NOT EXISTS idx_memories_session ON memories (session_id);
CREATE INDEX IF NOT EXISTS idx_memories_category ON memories (category);
CREATE INDEX IF NOT EXISTS idx_memories_importance ON memories (importance_score DESC);
CREATE INDEX IF NOT EXISTS idx_memories_active ON memories (is_active) WHERE is_active = true;

-- Memory Versions table: Historical audit trail when memories are consolidated or updated
CREATE TABLE IF NOT EXISTS memory_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    memory_id           UUID NOT NULL REFERENCES memories(id) ON DELETE CASCADE,
    version_number      INT NOT NULL,
    content             TEXT NOT NULL,
    reason_for_update   VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_memory_versions_lookup ON memory_versions (memory_id, version_number DESC);

-- Token Usage Metrics table: Real-time tracking of token savings and reduction percentages
CREATE TABLE IF NOT EXISTS token_usage_metrics (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id          UUID REFERENCES chat_sessions(id) ON DELETE SET NULL,
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
    project_id          VARCHAR(100),
    agent_id            VARCHAR(100),
    original_tokens     INT NOT NULL,
    optimized_tokens    INT NOT NULL,
    tokens_saved        INT NOT NULL,
    reduction_percentage DOUBLE PRECISION NOT NULL,
    optimization_mode   VARCHAR(50) NOT NULL,
    latency_ms          BIGINT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_token_usage_session ON token_usage_metrics (session_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_project ON token_usage_metrics (project_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_created ON token_usage_metrics (created_at DESC);

-- Context Cache table: Fast SHA-256 keyed cache storing pre-computed optimized contexts
CREATE TABLE IF NOT EXISTS context_cache (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_key           VARCHAR(64) UNIQUE NOT NULL,
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
    project_id          VARCHAR(100),
    cached_context      JSONB NOT NULL,
    token_count         INT NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_context_cache_lookup ON context_cache (cache_key, expires_at);

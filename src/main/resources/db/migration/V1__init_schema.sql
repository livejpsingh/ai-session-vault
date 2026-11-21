-- AI Session Vault V1 Schema Initialization

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Table: chat_sessions
CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_session_id VARCHAR(255) UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    provider VARCHAR(100),
    model_name VARCHAR(100),
    project_name VARCHAR(255),
    workspace_path TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: chat_messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    sequence_number INT NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    thinking TEXT,
    tool_calls JSONB,
    tool_call_id VARCHAR(255),
    tokens_used INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fts_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, '') || ' ' || coalesce(thinking, ''))) STORED
);

-- Table: tags
CREATE TABLE IF NOT EXISTS tags (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Table: session_tags (join table)
CREATE TABLE IF NOT EXISTS session_tags (
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    tag_id UUID NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, tag_id)
);

-- Table: attachments
CREATE TABLE IF NOT EXISTS attachments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    message_id UUID REFERENCES chat_messages(id) ON DELETE SET NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    storage_path TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance & search
CREATE INDEX IF NOT EXISTS idx_chat_sessions_external_id ON chat_sessions (external_session_id);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_project ON chat_sessions (project_name);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_provider_model ON chat_sessions (provider, model_name);
CREATE INDEX IF NOT EXISTS idx_chat_sessions_created ON chat_sessions (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_seq ON chat_messages (session_id, sequence_number ASC);
CREATE INDEX IF NOT EXISTS idx_chat_messages_fts ON chat_messages USING GIN (fts_vector);

CREATE INDEX IF NOT EXISTS idx_attachments_session ON attachments (session_id);

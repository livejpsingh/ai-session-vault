-- V5: Enterprise Architecture v3.0 - Multi-Tenancy Backfill (R4 Mitigation) & Transactional Outbox (R3 Mitigation)

-- 1. Multi-Tenancy Backfill for chat_sessions
ALTER TABLE chat_sessions ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) DEFAULT 'default';
UPDATE chat_sessions SET tenant_id = 'default' WHERE tenant_id IS NULL;
ALTER TABLE chat_sessions ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_chat_sessions_tenant ON chat_sessions (tenant_id);

-- 2. Multi-Tenancy Backfill for chat_messages
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(100) DEFAULT 'default';
UPDATE chat_messages SET tenant_id = 'default' WHERE tenant_id IS NULL;
ALTER TABLE chat_messages ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_chat_messages_tenant ON chat_messages (tenant_id);

-- 3. Transactional Outbox Table for Asynchronous Memory Extraction and Resilient Event Dispatch (R3 Mitigation)
CREATE TABLE IF NOT EXISTS outbox_events (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(100) NOT NULL DEFAULT 'default',
    aggregate_type      VARCHAR(100) NOT NULL,
    aggregate_id        VARCHAR(100) NOT NULL,
    event_type          VARCHAR(100) NOT NULL,
    payload             JSONB NOT NULL,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count         INT NOT NULL DEFAULT 0,
    last_error          TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_events_status ON outbox_events (status, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);

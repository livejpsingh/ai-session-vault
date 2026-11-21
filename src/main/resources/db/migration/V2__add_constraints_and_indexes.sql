-- AI Session Vault V2 Schema Enhancements: Constraints & Performance Indexes

-- Ensure uniqueness of sequence numbers per session
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_messages_session_seq ON chat_messages (session_id, sequence_number);

-- Index for lower-cased tag name lookups
CREATE INDEX IF NOT EXISTS idx_tags_name_lower ON tags (LOWER(name));

-- Index for session status filtering
CREATE INDEX IF NOT EXISTS idx_chat_sessions_status ON chat_sessions (status);

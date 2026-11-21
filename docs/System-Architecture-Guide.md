# AI Session Vault — Semantic Memory Architecture

## Overview

The semantic memory path augments the existing PostgreSQL full-text search without replacing the existing REST or MCP APIs. Raw transcripts and messages remain the source of truth in `chat_sessions` and `chat_messages`. Indexed turn-aware chunks are stored in `session_chunks`, with pgvector embeddings, generated full-text vectors, and provenance back to the source session.

The local profile uses Ollama (`nomic-embed-text`, 768 dimensions by default). The cloud profile uses OpenAI (`text-embedding-3-small`, configured to the same dimension). The database dimension is fixed when Flyway first creates the vector column; changing it requires a schema migration and a full reindex.

Workspace lifecycle hooks for GitHub Copilot (`.github/hooks/ai-session-vault.json`) and Antigravity (`.agents/hooks.json`) create a session at startup and upload the available transcript when the agent stops. These hooks require the service to be running and read `VAULT_API_KEY` from the process environment or the workspace `.env`; they never embed a default API key.

## Async ingestion and hybrid retrieval

```mermaid
sequenceDiagram
    autonumber
    participant Client as REST or MCP Client
    participant Ingest as Message/Transcript Service
    participant DB as PostgreSQL + pgvector
    participant Listener as After-commit Listener
    participant Worker as RAG Index Worker
    participant Chunker as ConversationChunker
    participant Model as Active Embedding Model
    participant Search as Hybrid Search Service

    Client->>Ingest: Ingest transcript or save messages
    Ingest->>DB: Persist raw messages and enqueue rag_index_jobs in one transaction
    Ingest->>DB: Commit
    DB-->>Listener: Publish index-request event after commit
    Listener->>Worker: Wake worker asynchronously on virtual thread
    Worker->>DB: Claim due job with lease (FOR UPDATE SKIP LOCKED)
    Worker->>DB: Load session and ordered messages
    Worker->>Chunker: Group user/model turns and split at paragraph/code-fence boundaries
    Chunker-->>Worker: Chunks, token counts, role sequence, source ranges
    Worker->>Model: Embed chunks in bounded batches
    Model-->>Worker: Vectors
    Worker->>DB: Atomically replace chunks and mark job INDEXED
    Note over Worker,DB: Failures are retried with exponential backoff; expired leases are recovered by polling.

    Client->>Search: POST /api/v1/rag/search (or semantic_recall MCP tool)
    Search->>Model: Embed query
    Model-->>Search: Query vector
    Search->>DB: Dense cosine candidates + sparse tsvector candidates
    DB-->>Search: RRF scores, metadata filters, recency boost, provenance
    Search->>Search: Apply MMR and same-session near-duplicate suppression
    Search-->>Client: Ranked grounded chunks and session metadata
```

## Persistence and search

- `session_chunks.session_id` references `chat_sessions.id` with `ON DELETE CASCADE`.
- `session_chunks.embedding` is `vector(${VAULT_RAG_EMBEDDING_DIMENSION})`; its HNSW index uses cosine distance (`m=16`, `ef_construction=64`).
- `search_vector` is generated from chunk content and indexed with GIN. Chunk metadata also has a GIN index.
- `rag_index_jobs` is the durable outbox and status record (`PENDING`, `PROCESSING`, `INDEXED`, `FAILED`). The event listener is a low-latency wake-up; the polling worker recovers work after restarts.
- Hybrid ranking uses weighted reciprocal rank fusion. `hybridWeight` is the dense contribution from 0 to 1; sparse weight is `1 - hybridWeight`. `minScore` is a cosine-similarity floor from 0 to 1. A configurable exponential half-life boost favors recent sessions.
- MMR greedily balances normalized relevance against cosine redundancy and suppresses near-identical chunks from the same session.
- Filters map `project` to `chat_sessions.project_name`, `agent` to `chat_sessions.metadata.agent`, `tags` to the existing `session_tags` relationship, and arbitrary JSON containment to chunk metadata.

## Operational requirements

1. Use a PostgreSQL build that provides the `vector` and `pg_trgm` extensions. Compose already uses `pgvector/pgvector:pg16`; production database operators must install/enable both extensions and grant the migration role permission to create them.
2. Set `POSTGRES_PASSWORD` and `VAULT_API_KEY` in an untracked `.env` file. Do not use shared or default credentials.
3. For `local-rag`, run Ollama and provide the Nomic BERT `vocab.txt` at `OLLAMA_TOKENIZER_VOCAB_PATH`. The model tokenizer path is required for accurate 800-token chunk limits.
4. For `cloud-rag`, set `OPENAI_API_KEY`. The selected model must return vectors matching the migrated dimension.
5. Keep `embedding_model` stable while indexing/searching. Reindex after changing models. Re-embedding cannot convert vectors across different dimensions without a database migration.
6. Monitor `rag_index_jobs` for permanent `FAILED` jobs with `GET /api/v1/rag/reindex/{sessionId}` and use the reindex endpoints after fixing provider or database errors.

When upgrading an existing Compose database initialized with the previous hard-coded password, first place that current password in `.env` so the existing volume remains accessible. Rotate the database role password through a privileged, private administrative session, then update `.env`; changing the Compose environment alone does not rotate credentials stored in an existing PostgreSQL volume.

The existing full-text search endpoints continue to search `chat_messages`; the RAG endpoint is additive.

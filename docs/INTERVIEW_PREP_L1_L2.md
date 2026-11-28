# AI Backend Engineer Interview Guide (L1 & L2) — AI Session Vault Edition

**Target Profile:** Backend / AI Engineer (~2 Years Experience)  
**Project Context:** **AI Session Vault** (Java 25 LTS, Spring Boot 4.2.0, Spring AI 2.0.3 MCP Server, PostgreSQL 16/18 + `pgvector`, Flyway V1–V5, Hybrid RAG, Knapsack Token Optimizer)

---

## Round 1 (L1): Core Backend, Spring Boot, PostgreSQL & Security

### Q1. Why does AI Session Vault enable Java 25 Virtual Threads (`spring.threads.virtual.enabled: true`), and when do they help?
**Answer:**  
AI Session Vault is predominantly **I/O-bound** — handling concurrent IDE hook ingestions (`POST /api/transcripts/ingest`), PostgreSQL/pgvector queries, and MCP tool calls. Virtual threads (Project Loom) allow each request to block on JDBC/HTTP I/O without pinning a heavyweight OS platform thread, improving throughput under concurrent IDE sessions while keeping simple synchronous Spring MVC code.  
*Caveat (2-year depth):* CPU-bound work (e.g., regex extraction or tokenization) still consumes carrier threads, and synchronized blocks around I/O in older libraries should be avoided or replaced with `ReentrantLock` to prevent pinning.

---

### Q2. Why is `spring.jpa.open-in-view: false` configured in `application.yml`?
**Answer:**  
With Open Session in View (`OSIV = true`), Hibernate holds a database connection open for the entire HTTP request/response lifecycle, including JSON serialization. Setting `open-in-view: false` forces all lazy associations to be resolved inside `@Transactional` service methods and mapped to DTOs (`ChatSessionDto`, `ChatMessageDto`), preventing connection-pool exhaustion under load and catching accidental `LazyInitializationException`s during testing.

---

### Q3. Explain how Flyway migrations (`V1`–`V5`) work in this project and why `V4` checks `pg_extension` before creating `vector(1536)`.
**Answer:**  
Flyway runs deterministic, versioned SQL migrations on startup (`V1__init_schema.sql` through `V5__add_v3_enterprise_capabilities.sql`).  
In `V4__add_intelligent_memory_and_token_optimizer.sql`, the `DO $$ ... IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'vector') ... END $$` block checks if `pgvector` is installed:
- If `pgvector` is present, it creates `memories` with `embedding vector(1536)` and an HNSW index.
- If `pgvector` is absent (e.g., a standard local PostgreSQL install), it still creates the relational table and `GIN` full-text index so core chat-session persistence works without failing startup.  
**Rule:** Never edit an already-applied migration (`V1`–`V5`); always add `V6__...`.

---

### Q4. How does API Key authentication and zero-downtime key rotation work?
**Answer:**  
`ApiKeyAuthenticationFilter` intercepts all requests (except `/actuator/health` and whitelisted Swagger/MCP discovery paths) and validates the `X-API-KEY` header using constant-time comparison to prevent timing attacks.  
For zero-downtime rotation, `application.yml` supports both `vault.security.api-key` (`VAULT_API_KEY`) and `vault.security.secondary-api-key` (`VAULT_SECONDARY_API_KEY`). During a 24-hour rotation window, both keys are accepted; once all IDE hooks and MCP clients are updated to the new key, the old key is retired.

---

### Q5. Why does PostgreSQL Full-Text Search use a generated `TSVECTOR` column with a `GIN` index instead of `ILIKE '%query%'`?
**Answer:**  
`ILIKE '%term%'` causes a full sequential table scan (`O(N)`) because leading wildcards cannot use standard B-tree indexes. In `V4`, `memories` defines:
```sql
search_vector TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', coalesce(content, ''))) STORED
```
indexed with `CREATE INDEX idx_memories_search_vector ON memories USING GIN (search_vector);`. This tokenizes, stems, and removes stop-words at write time, enabling sub-millisecond `@@ plainto_tsquery(...)` lexical lookups even as `chat_messages` and `memories` grow to hundreds of thousands of rows.

---

### Q6. What security risks exist when ingesting raw IDE chat transcripts, and how does the vault mitigate them?
**Answer:**  
1. **Leaked Credentials:** Developers often paste API keys or passwords into AI chats. `SecretRedactionService` scans and redacts secrets before persistence.
2. **Null-Byte / Encoding Crashes:** PostgreSQL `TEXT` columns reject `\u0000` bytes; `TranscriptIngestionServiceImpl` strips null bytes before saving.
3. **Prompt Injection:** Stored memories could contain adversarial instructions (`"Ignore previous instructions..."`). `PromptGuardrailService` evaluates queries/content and attaches security warnings.
4. **DoS / Abuse:** `RateLimitingFilter` applies token-bucket rate limiting per client.

---

### Q7. How do you test database queries and Spring Boot endpoints reliably in CI?
**Answer:**  
- **Unit Tests (JUnit 5 + Mockito + AssertJ):** Test business logic in isolation (e.g., `ContextOptimizationEngineTest`, `MemoryExtractionEngineTest`, `PerformanceAndReductionBenchmarkTest`).
- **Integration Tests (Testcontainers):** Boot a real `pgvector/pgvector:pg16` container in CI (`ci.yml` / `cd.yml`) to verify Flyway `V1`–`V5`, native SQL (`HybridSearchRepository`), and HNSW/GIN queries.
- **Coverage (JaCoCo):** `jacoco-maven-plugin` instruments Surefire via `@{argLine}` in `pom.xml` and generates `target/site/jacoco/jacoco.xml`.

---

### Q8. How does the auto-capture architecture work across IDEs without blocking the developer's chat?
**Answer:**  
Editor lifecycle hooks (`.agents/hooks.json` for Google Antigravity, `.github/hooks/ai-session-vault.json` for GitHub Copilot) trigger `scripts/session-vault-hook.ps1` (or `.sh`) on `SessionStart` and `Stop`.  
1. `SessionStart` checks `/actuator/health` and registers/backfills the session in `chat_sessions`.
2. `Stop` posts the transcript path or messages to `POST /api/transcripts/ingest` with a short HTTP timeout, failing silently if the vault is down so the IDE never freezes.
3. The service persists raw messages synchronously and defers heavy embedding/indexing to the transactional outbox worker.

---

## Round 2 (L2): AI Backend Engineering, Hybrid RAG, MCP & System Design

### Q1. What is the Model Context Protocol (MCP), and how does AI Session Vault expose it?
**Answer:**  
MCP is a standardized JSON-RPC protocol that lets AI agents (Claude, Antigravity, Copilot, Cursor) discover and invoke external tools and resources.  
AI Session Vault uses `spring-ai-starter-mcp-server-webmvc` (`transport: streamable-http` on `/mcp`) to expose **19 tools**:
- **6 Session Tools (`vault_*`):** `vault_save_message`, `vault_ingest_transcript`, `vault_get_session`, `vault_list_sessions`, `vault_search_messages`, `vault_export_session`.
- **13 Memory & RAG Tools (`memory_*`):** `memory_optimize_context`, `memory_search`, `memory_recall`, `memory_store`, `memory_update`, `memory_forget`, `memory_summarize`, `memory_consolidate`, `memory_get_profile`, `memory_get_decisions`, `memory_get_timeline`, `memory_explain_retrieval`, `memory_get_stats`.  
For stdio-only clients (like Claude Desktop), `scripts/mcp-bridge.js` bridges stdio to the HTTP `/mcp` endpoint.

---

### Q2. Explain the Hybrid RAG retrieval pipeline (`pgvector` + GIN FTS + RRF + MMR). Why not use pure vector search?
**Answer:**  
Pure dense vector search struggles with **exact identifiers** (class names like `OutboxEventWorker`, error codes, CLI flags like `--batch-mode`), whereas pure keyword search misses **semantic paraphrases** (*"database failover"* vs. *"Patroni streaming replica"*).  
AI Session Vault combines both in `HybridSearchRepository`:
1. **Dense Retrieval:** `pgvector` HNSW cosine similarity (`m=16`, `ef_construction=64`) over `1536`-dim embeddings.
2. **Sparse Retrieval:** PostgreSQL GIN `tsvector` ranking.
3. **Weighted Reciprocal Rank Fusion (RRF):** Combines ranks from both lists using $\text{score} = \sum \frac{w_i}{k + \text{rank}_i}$ (default `rrf-k = 60`) plus a recency decay boost (`recency-half-life = 90d`).
4. **MMR Diversification:** Reduces redundant near-duplicate chunks before returning results.

---

### Q3. How does `ConversationChunker` split transcripts, and how are tokens counted accurately across LLM providers?
**Answer:**  
Naive fixed-character splitting breaks code blocks and mid-sentence context. `ConversationChunker` splits turns respecting **paragraph boundaries and Markdown code fences** (`max-tokens: 800`, `overlap-tokens: 100`).  
For token counting, we avoid rough `chars / 4` heuristics and use real model tokenizers:
- **`JTokkitTokenCounter` (`CL100K_BASE`):** Exact BPE token counts for OpenAI / Claude-compatible budgets.
- **`BertWordPieceTokenCounter`:** WordPiece tokenization aligned with Gemini / local embedding models (`nomic-embed-text`).

---

### Q4. Why does AI Session Vault use `$0`-cost deterministic memory extraction (`MemoryExtractionEngineImpl`) instead of calling an LLM on every message?
**Answer:**  
Calling an external LLM after every chat turn adds latency, rate-limit risk, and recurring API cost. `MemoryExtractionEngineImpl` uses compiled regex patterns and tech-stack matchers to deterministically extract 5 high-signal categories at **$0 LLM cost** in `< 20 ms`:
- `DECISION` (*"we decided to..."*, *"switched to..."*)
- `PROJECT` (frameworks, DBs, versions)
- `PREFERENCE` (*"always use..."*, *"prefer..."*)
- `PROCEDURAL` (CLI commands like `.\mvnw test`, `docker compose up -d`)
- `ACTION` (`TODO:`, `FIXME:`, `ACTION ITEM:`)  
When facts evolve, old versions are archived in `memory_versions` for full auditability.

---

### Q5. Walk through the Knapsack Token Budget Optimization Engine (`ContextOptimizationEngineImpl`) and its zero-overrun guarantee.
**Answer:**  
Given a `tokenBudget` (e.g. `500` tokens) and `OptimizationMode` (`QUALITY_FIRST`, `BALANCED`, `AGGRESSIVE`):
1. **Cache Check:** Computes a SHA-256 key over `(tenantId, projectId, query, budget, mode)` in `context_cache` for `< 30 ms` cache hits.
2. **Compression & Deduplication:** Strips conversational fluff (`"Sure! Here is..."`) in `BALANCED`/`AGGRESSIVE` modes while preserving code blocks in `QUALITY_FIRST`, and deduplicates identical content signatures.
3. **Greedy Knapsack Packing:** Sorts candidates by relevance/importance score and packs items while `tokensUsed + item.tokenCost <= budget`. If a high-importance item doesn't fit, it falls back to `shortSummary`.
4. **Hard Safety Invariant:** After rendering Markdown headers, if `finalTokens > budget`, items are trimmed from lowest priority (down to empty + warning if even 1 item + headers exceeds `budget`), guaranteeing `used <= budget` in 100% of calls.

---

### Q6. How does the Transactional Outbox (`outbox_events`) guarantee zero message loss during async indexing?
**Answer:**  
If an app updates PostgreSQL and then calls an external embedding API or message broker synchronously, a crash or network timeout leaves the DB and vector index out of sync.  
With the **Transactional Outbox** (`V5`):
1. `TranscriptIngestionService` writes `chat_messages` **and** inserts a `PENDING` row into `outbox_events` inside the **same ACID database transaction**.
2. The HTTP response returns immediately (`< 500 ms`).
3. `OutboxEventWorker` polls `outbox_events` using `SELECT ... FOR UPDATE SKIP LOCKED` (safe across multiple app replicas), runs chunking/embedding/extraction, and atomically marks the event `PROCESSED` (or increments `retry_count` / marks `FAILED` with `last_error`).

---

### Q7. How is stale context prevented in `context_cache`, and how would you scale this to multiple instances?
**Answer:**  
- **Invalidation:** Any mutation (`memory_store`, `memory_update`, `memory_forget`) explicitly calls `invalidateCache(projectId)` to evict stale SHA-256 entries, paired with `expires_at` TTLs.
- **Multi-Instance Scaling:** Because `context_cache` lives in PostgreSQL (and can be fronted by Redis in `multi-layer-cache` mode), all app instances share cache state and invalidations. For PostgreSQL HA, place **PgBouncer** (`transaction` pooling with `prepareThreshold=0`) in front of a **Patroni / CloudNativePG** primary-replica cluster, routing `OutboxEventWorker` writes to the primary and read-heavy analytics to replicas.

---

### Q8. System Design Exercise: How would you extend AI Session Vault to support 1,000 developers in an enterprise?
**Answer (Key Talking Points):**
1. **Multi-Tenancy & Isolation:** Enforce `tenant_id` (added in `V5`) at the repository/RLS (Row-Level Security) layer and bind `tenant_id` to scoped API keys or OAuth2/JWT claims.
2. **Async Ingestion & Backpressure:** Move `OutboxEventWorker` to Kafka/SQS (via the existing `vault.features.kafka` flag) for horizontal worker scaling of embedding generation.
3. **PostgreSQL HA & Partitioning:** Use Patroni or CloudNativePG with streaming replicas + WAL archiving (`pgBackRest`), partition `chat_messages` by `created_at` month, and tune `maintenance_work_mem` + `hnsw.ef_search` for `pgvector`.
4. **Observability:** Export `VaultMetricsService` Micrometer metrics (`vault.transcripts.ingested.total`, `vault.tokens.saved.total`, `vault.optimization.duration`) to Prometheus/Grafana with SLO alerts on outbox lag and p99 latency.

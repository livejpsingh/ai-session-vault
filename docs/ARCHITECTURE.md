# System Architecture Design Document: AI Session Vault 2.0
### Intelligent Agent Memory, Hybrid RAG & Knapsack Token Optimization Platform

**Document Version:** 2.0.0  
**Status:** Approved & Production-Ready  
**Target Runtimes:** Java 21 LTS / Spring Boot 4.1.2 / Spring AI 2.0.1  
**Persistence Tier:** PostgreSQL 16/18 with `pgvector` & GIN Full-Text Search  
**Protocol Integrations:** Model Context Protocol (MCP 19 Tools), REST API, VS Code Lifecycle Hooks  

---

## 1. Executive Summary & Architectural Goals

**AI Session Vault 2.0** is an enterprise-grade conversation persistence, hierarchical memory, and token-budget optimization platform designed for autonomous AI agents (Google Antigravity, GitHub Copilot, Claude Code, Cursor) and developer engineering teams.

```
       +-------------------------------------------------------------+
       |                  DEVELOPER WORKSPACE (VS CODE)              |
       |     Google Antigravity  /  GitHub Copilot Chat Turns        |
       +------------------------------+------------------------------+
                                      | (Raw Transcripts: 50,000+ Tokens)
                                      v
       +-------------------------------------------------------------+
       |                   AI SESSION VAULT 2.0 CORE                 |
       |  +-------------------------------------------------------+  |
       |  |  4-Tier Memory Hierarchy (L1, L2, L3, L4 Archive)     |  |
       |  |  $0 Heuristic Memory Extraction Engine (Regex/AST)    |  |
       |  |  Knapsack Token Budget Optimizer (70%-90% Reduction)  |  |
       |  |  SHA-256 Context Cache (<30ms Latency)                |  |
       |  |  Spring AI MCP Server (19 Tools: 13 Memory + 6 Vault) |  |
       |  +-------------------------------------------------------+  |
       +------------------------------+------------------------------+
                                      | (Optimized Payload: <=800 Tokens)
                                      v
       +-------------------------------------------------------------+
       |               TARGET LARGE LANGUAGE MODELS (LLMs)           |
       |       Anthropic Claude 3.7  |  Google Gemini 2.5  | GPT-4o  |
       +-------------------------------------------------------------+
```

### 1.1 Core Engineering Goals
1. **Durable Multi-Tier Persistence**: Ingest and audit 100% of conversational turns, reasoning traces (`thinking`), and tool executions without blocking the developer's chat loop.
2. **70% to 90% Token Reduction**: Apply mathematical knapsack budget packing to compress historical transcripts down to strict token bounds with **zero silent overruns**.
3. **$0-LLM-Cost Knowledge Extraction**: Parse high-value architectural decisions, stack rules, developer preferences, and verified runbooks using deterministic heuristic extractors.
4. **Hybrid RAG Retrieval Engine**: Combine dense vector similarity (`pgvector` HNSW) with sparse lexical search (PostgreSQL GIN `tsvector`) fused via Weighted Reciprocal Rank Fusion (RRF) and Maximal Marginal Relevance (MMR).
5. **Universal Model Context Protocol (MCP) Support**: Provide 19 standard tools accessible over Streamable HTTP (`/mcp`) and stdio transport bridges.

---

## 2. High-Level Design (HLD)

### 2.1 System Context & High-Level Block Architecture

```mermaid
flowchart TD
    subgraph Workspaces["Developer Workspaces & IDEs"]
        Antigravity["Google Antigravity Agent<br/>(VS Code Workspace)"]
        Copilot["GitHub Copilot Chat<br/>(VS Code Extension)"]
        ExternalAgent["Autonomous Coding Agents<br/>(Claude Code / Cursor)"]
        Hooks["Lifecycle Hook Interceptors<br/>(SessionStart / Stop Events)"]
    end

    subgraph SecurityGate["Stateless Security Boundary"]
        ApiKeyFilter["ApiKeyAuthenticationFilter<br/>(Constant-time X-API-KEY / query param)"]
        SecConfig["VaultSecurityConfig<br/>(Stateless Session, CSRF Disabled)"]
    end

    subgraph CoreEngine["AI Session Vault 2.0 Core Platform"]
        subgraph GatewayMCP["Ingress Controllers & Gateway"]
            MCP_Server["Spring AI MCP Server (/mcp)<br/>19 Tools (13 Memory + 6 Session)"]
            REST_Gateway["AgentContextGateway (/api/agents/context)"]
            REST_Memory["MemoryController (/api/memory/**)"]
            REST_Sessions["SessionController (/api/sessions/**)"]
            REST_Ingest["TranscriptIngestionController (/api/transcripts/ingest)"]
        end

        subgraph Engines["Intelligence & Optimization Engines"]
            Extractor["MemoryExtractionEngine<br/>($0-Cost Deterministic Parser)"]
            Optimizer["ContextOptimizationEngine<br/>(Knapsack Token Budget Packer)"]
            Tokenizer["Model Tokenizers<br/>(JTokkit BPE / BertWordPiece)"]
            CacheMgr["SHA-256 Context Cache Manager<br/>(Sub-ms Memory Lookup)"]
            SearchEngine["Hybrid RAG Search Service<br/>(Dense Vector + GIN FTS + RRF)"]
        end

        subgraph MemoryHierarchy["4-Tier Hierarchical Memory Model"]
            L1["Level 1: Working Memory<br/>(Active turn context & immediate goals)"]
            L2["Level 2: Session Memory<br/>(Summaries & milestone outcomes)"]
            L3["Level 3: Long-Term Memory<br/>(Architectural decisions, tech stack, preferences)"]
            L4["Level 4: Archive Memory<br/>(Immutable raw transcripts in chat_messages)"]
        end

        subgraph JPA["Spring Data JPA Repositories"]
            MemRepo["MemoryRepository"]
            MemVerRepo["MemoryVersionRepository"]
            MetricsRepo["TokenUsageMetricRepository"]
            CacheRepo["ContextCacheRepository"]
            SessionRepo["ChatSessionRepository"]
            MsgRepo["ChatMessageRepository"]
        end
    end

    subgraph PersistenceTier["PostgreSQL 16/18 + pgvector"]
        MemTable[("memories<br/>(GIN FTS + pgvector HNSW)")]
        VerTable[("memory_versions<br/>(Audit History)")]
        MetricTable[("token_usage_metrics<br/>(Savings Analytics)")]
        CacheTable[("context_cache<br/>(SHA-256 Hashes)")]
        SessionTable[("chat_sessions")]
        MsgTable[("chat_messages<br/>(FTS fts_vector)")]
    end

    Antigravity --> Hooks
    Copilot --> Hooks
    Hooks -->|HTTP POST JSONL| ApiKeyFilter
    ExternalAgent -->|MCP Streamable HTTP / stdio| ApiKeyFilter

    ApiKeyFilter --> SecConfig
    SecConfig --> GatewayMCP

    REST_Ingest --> Extractor
    Extractor --> MemRepo
    Extractor --> MemVerRepo

    REST_Memory --> Optimizer
    MCP_Server --> Optimizer
    REST_Gateway --> Optimizer

    Optimizer --> CacheMgr
    Optimizer --> SearchEngine
    Optimizer --> Tokenizer

    GatewayMCP --> MemoryHierarchy
    MemoryHierarchy --> JPA
    JPA --> PersistenceTier
```

### 2.2 4-Tier Memory Hierarchy Architecture

```
+-----------------------------------------------------------------------------------------+
| LEVEL 1: WORKING MEMORY                                                                 |
| Scope: Current Turn / Active Task                                                       |
| Contents: User query, immediate agent thinking trace, open execution state              |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| LEVEL 2: SESSION MEMORY                                                                 |
| Scope: Conversation Session Lifecycle                                                   |
| Contents: Turn summaries, completed sub-tasks, unresolved action items, session tags    |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| LEVEL 3: LONG-TERM MEMORY (PROJECT-SCOPED)                                              |
| Scope: Permanent / Cross-Session Project Memory                                         |
| Contents: Architectural decisions (ADRs), tech stack rules, developer conventions,       |
|           verified runbook commands, historical version audits                          |
+-----------------------------------------------------------------------------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
| LEVEL 4: COLD ARCHIVE MEMORY                                                            |
| Scope: Immutable Relational Storage                                                     |
| Contents: Raw JSONL message payloads, tool call inputs/outputs, token metrics           |
+-----------------------------------------------------------------------------------------+
```

---

## 3. Low-Level Design (LLD)

### 3.1 Class & Component Architecture

```mermaid
classDiagram
    class ContextOptimizationEngine {
        <<interface>>
        +optimizeContext(request: OptimizeContextRequest) OptimizeContextResponse
        +invalidateCache(projectId: String) void
        +getSavingsMetrics(projectId: String) TokenSavingsSummaryDto
    }

    class ContextOptimizationEngineImpl {
        -MemoryRepository memoryRepository
        -ContextCacheRepository contextCacheRepository
        -TokenUsageMetricRepository metricRepository
        -JTokkitTokenCounter jTokkitCounter
        -BertWordPieceTokenCounter wordPieceCounter
        +optimizeContext(request: OptimizeContextRequest) OptimizeContextResponse
        -computeSha256(input: String) String
        -packBudgetKnapsack(candidates: List, budget: int, mode: OptimizationMode) PackedResult
    }

    class MemoryExtractionEngine {
        <<interface>>
        +extractMemories(sessionId: UUID, transcript: List~ChatMessage~) List~Memory~
        +extractFromText(text: String, projectId: String) List~Memory~
    }

    class MemoryExtractionEngineImpl {
        -MemoryRepository memoryRepository
        -MemoryVersionRepository versionRepository
        -Pattern DECISION_PATTERN
        -Pattern STACK_PATTERN
        -Pattern PREFERENCE_PATTERN
        -Pattern RUNBOOK_PATTERN
        -Pattern ACTION_PATTERN
        +extractMemories(sessionId: UUID, transcript: List~ChatMessage~) List~Memory~
        -detectAndUpsert(extracted: ExtractedFact) Memory
    }

    class AgentContextGateway {
        <<interface>>
        +assembleContext(agentId: String, projectId: String, query: String, budget: int) AgentContextPayload
    }

    class AgentContextGatewayImpl {
        -ContextOptimizationEngine optimizationEngine
        -MemoryRepository memoryRepository
        +assembleContext(agentId: String, projectId: String, query: String, budget: int) AgentContextPayload
    }

    class MemoryMcpTools {
        -ContextOptimizationEngine optimizationEngine
        -MemoryRepository memoryRepository
        +memory_optimize_context(query: String, projectId: String, tokenBudget: Integer) String
        +memory_search(query: String, projectId: String, limit: Integer) String
        +memory_recall(sessionId: String, limit: Integer) String
        +memory_store(content: String, category: String, projectId: String) String
        +memory_update(memoryId: String, content: String) String
        +memory_forget(memoryId: String) String
        +memory_get_profile(projectId: String) String
        +memory_get_decisions(projectId: String) String
        +memory_get_stats(projectId: String) String
    }

    class Memory {
        +UUID id
        +String projectId
        +UUID sessionId
        +MemoryCategory category
        +MemoryTier tier
        +String content
        +Double importanceScore
        +Integer accessCount
        +Boolean isActive
        +Instant lastAccessedAt
        +Instant createdAt
        +Instant updatedAt
    }

    class MemoryVersion {
        +UUID id
        +UUID memoryId
        +String previousContent
        +String changeReason
        +Integer versionNumber
        +Instant createdAt
    }

    class TokenUsageMetric {
        +UUID id
        +String projectId
        +String modelName
        +Integer rawPromptTokens
        +Integer optimizedPromptTokens
        +Integer tokensSaved
        +Double reductionPercentage
        +Instant createdAt
    }

    class ContextCacheEntry {
        +UUID id
        +String queryHash
        +String projectId
        +String cachedPrompt
        +Integer tokenCount
        +Instant expiresAt
        +Instant createdAt
    }

    ContextOptimizationEngine <|.. ContextOptimizationEngineImpl
    MemoryExtractionEngine <|.. MemoryExtractionEngineImpl
    AgentContextGateway <|.. AgentContextGatewayImpl
    MemoryMcpTools --> ContextOptimizationEngine
    AgentContextGatewayImpl --> ContextOptimizationEngine
    ContextOptimizationEngineImpl --> Memory
    ContextOptimizationEngineImpl --> ContextCacheEntry
    ContextOptimizationEngineImpl --> TokenUsageMetric
    MemoryExtractionEngineImpl --> Memory
    MemoryExtractionEngineImpl --> MemoryVersion
```

### 3.2 Algorithmic Specifications

#### 3.2.1 Knapsack Token Budget Packing Algorithm
The optimization engine treats memory inclusion as a **0/1 Knapsack Problem** where each candidate memory $m_i$ has:
- **Cost**: Exact token count $w_i = \text{Tokenize}(m_i, \text{Model})$.
- **Value**: Composite weight $V_i = \alpha \cdot \text{RelevanceScore}_i + \beta \cdot \text{ImportanceScore}_i + \gamma \cdot \text{RecencyBoost}_i$.
- **Constraint**: $\sum_{i \in \text{Selected}} w_i \le \text{tokenBudget}$.

```
Algorithm: KnapsackContextPacking
Input: candidates (List<Memory>), tokenBudget (int), mode (OptimizationMode), model (String)
Output: PackedContext (String, actualTokens, reductionPercentage)

1. Compute token count w_i for each memory m_i using target model tokenizer (JTokkit/WordPiece).
2. Apply mode distillation:
     - QUALITY_FIRST : retain exact text
     - BALANCED      : strip markdown filler, conversational boilerplate
     - AGGRESSIVE    : compress into compact key-value lines
3. Re-calculate distilled token cost w_i'.
4. Sort candidates descending by Value Density: D_i = V_i / w_i'.
5. Initialize currentTokens = 0, selected = [].
6. For each candidate m_i in sorted candidates:
     If currentTokens + w_i' <= tokenBudget:
         selected.append(m_i)
         currentTokens += w_i'
     Else if mode == AGGRESSIVE and remainingBudget > 15:
         Truncate m_i to remainingBudget, append, break.
7. Return FormatContext(selected), currentTokens, (1 - currentTokens/rawTokens)*100.
```

#### 3.2.2 $0-Cost Heuristic Knowledge Extraction Patterns
The extractor processes turns sequentially on the `Stop` event using pre-compiled regex automata:

| Category | Trigger Patterns & Heuristic Rules | Extracted Memory Representation |
|:---|:---|:---|
| `DECISION` | `(?i)(?:decided to\|we will use\|chosen\|architectural choice:)\s+(.+)` | Clean architectural statement with rationale |
| `PROJECT` | `(?i)(?:framework:\|database:\|port:\|version:\|stack:)\s+([a-zA-Z0-9_\-\.\s]+)` | Normalized tech stack key-value entry |
| `PREFERENCE` | `(?i)(?:prefer\|always use\|never use\|convention:)\s+(.+)` | Developer tooling convention rule |
| `PROCEDURAL` | `(?i)(?:runbook:\|verified command:\|to build:)\s*(?:```[\w]*\n)?([^`\n]+)` | Executable shell command runbook |
| `ACTION` | `(?i)(?:TODO:\|FIXME:\|next step:\|unresolved:)\s+(.+)` | Open backlog action item |

---

## 4. Sequence Diagrams

### 4.1 Automated VS Code Transcript Ingestion & Extraction Flow

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer (VS Code)
    participant Agent as Antigravity / Copilot
    participant Hook as Lifecycle Hook (Stop Event)
    participant Filter as ApiKeyAuthenticationFilter
    participant IngestCtrl as TranscriptIngestionController
    participant IngestSvc as TranscriptIngestionService
    participant Extractor as MemoryExtractionEngine
    participant DB as PostgreSQL (ai_session_vault)

    Dev->>Agent: Prompt / Coding Task Instruction
    Agent-->>Dev: Generates Code, Thinking & Tool Invocations
    Agent->>Hook: Fire "Stop" Lifecycle Hook
    Hook->>Hook: Read transcript.jsonl & sanitize null bytes
    Hook->>Filter: POST /api/transcripts/ingest (Payload + X-API-KEY)
    Filter->>Filter: Constant-time API Key Validation
    Filter->>IngestCtrl: Dispatch Request
    IngestCtrl->>IngestSvc: ingestTranscript(request)
    
    rect rgb(240, 248, 255)
        Note over IngestSvc,DB: Transaction Boundary: Chat Persistence
        IngestSvc->>DB: Upsert chat_session record
        IngestSvc->>DB: Insert chat_messages (idempotent sequence check)
    end

    IngestSvc->>Extractor: extractMemories(sessionId, messages)
    rect rgb(245, 255, 245)
        Note over Extractor,DB: $0 Heuristic Knowledge Extraction
        Extractor->>Extractor: Regex scan (Decisions, Runbooks, Preferences)
        Extractor->>DB: Check existing memory (fingerprint match)
        alt Fact Changed
            Extractor->>DB: Archive old content in memory_versions
            Extractor->>DB: Update memory with new version
        else New Fact
            Extractor->>DB: Insert new memory record
        end
    end

    IngestSvc-->>IngestCtrl: IngestResult (imported turns, extracted count)
    IngestCtrl-->>Hook: 200 OK (JSON Summary)
```

### 4.2 Knapsack Token-Budget Optimization Flow

```mermaid
sequenceDiagram
    autonumber
    actor Agent as Coding Agent (Claude / Antigravity)
    participant MCP as MemoryMcpTools (/mcp)
    participant OptEngine as ContextOptimizationEngine
    participant Cache as SHA-256 Context Cache
    participant RAG as Hybrid Search Service
    participant Tokenizer as JTokkit / WordPiece Tokenizer
    participant DB as PostgreSQL 16 (pgvector + GIN)
    participant Metrics as TokenUsageMetricRepository

    Agent->>MCP: memory_optimize_context(query, projectId, tokenBudget=500, mode=BALANCED)
    MCP->>OptEngine: optimizeContext(request)
    
    OptEngine->>OptEngine: Compute SHA-256(query + projectId + budget + mode)
    OptEngine->>Cache: Lookup queryHash

    alt Cache Hit (<30ms)
        Cache-->>OptEngine: Return Cached ContextEntry
        OptEngine-->>MCP: OptimizedContextResponse (fromCache=true, latency <30ms)
        MCP-->>Agent: JSON Prompt Payload (88 Tokens)
    else Cache Miss
        OptEngine->>RAG: searchHybrid(query, projectId, limit=20)
        RAG->>DB: Dense Vector (HNSW cosine) + GIN FTS (tsvector)
        DB-->>RAG: Matched rows with relevance scores
        RAG->>RAG: Compute Weighted RRF & MMR Rerank
        RAG-->>OptEngine: Ranked candidate memories
        
        loop For Each Memory Candidate
            OptEngine->>Tokenizer: countTokens(candidate.content)
            Tokenizer-->>OptEngine: rawTokens
            OptEngine->>OptEngine: Distill text based on BALANCED mode
            OptEngine->>Tokenizer: countTokens(distilledContent)
            Tokenizer-->>OptEngine: packedTokens
        end

        OptEngine->>OptEngine: Execute Knapsack Budget Packing (budget=500)
        OptEngine->>Cache: Store packed context (queryHash, expires in 24h)
        OptEngine->>Metrics: Record TokenUsageMetric (raw: 50,000, opt: 88, saved: 49,912)
        OptEngine-->>MCP: OptimizedContextResponse (fromCache=false, reduction=82.4%)
        MCP-->>Agent: JSON Prompt Payload (88 Tokens)
    end
```

### 4.3 Hybrid RAG Search Flow with Dense Vector + GIN FTS

```mermaid
sequenceDiagram
    autonumber
    participant Caller as Agent / REST Client
    participant SearchService as HybridSearchService
    participant EmbeddingModel as Embedding Service (OpenAI / Ollama)
    participant DB as PostgreSQL (pgvector + GIN)

    Caller->>SearchService: searchHybrid(query="database migrations", projectId="vault")
    
    par Dense Vector Search
        SearchService->>EmbeddingModel: getEmbedding(query)
        EmbeddingModel-->>SearchService: vector[1536]
        SearchService->>DB: SELECT id, content, 1 - (embedding <=> :vec) AS cos_sim<br/>FROM memories WHERE is_active=true ORDER BY embedding <=> :vec LIMIT 20
        DB-->>SearchService: Vector Candidates (Rank_dense)
    and Sparse Lexical Search
        SearchService->>DB: SELECT id, content, ts_rank_cd(fts_vector, plainto_tsquery(:query)) AS lex_rank<br/>FROM memories WHERE fts_vector @@ plainto_tsquery(:query) LIMIT 20
        DB-->>SearchService: Keyword Candidates (Rank_sparse)
    end

    SearchService->>SearchService: Fuse with RRF: Score = (w_dense / (60 + Rank_dense)) + (w_sparse / (60 + Rank_sparse))
    SearchService->>SearchService: Apply MMR (Maximal Marginal Relevance) for diversity
    SearchService-->>Caller: List<MemorySearchResult> (sorted by fused RRF score)
```

---

## 5. Complete Entity-Relationship (ER) Diagram

```mermaid
erDiagram
    chat_sessions ||--o{ chat_messages : "contains (1:N)"
    chat_sessions ||--o{ session_tags : "labeled by (N:M)"
    tags ||--o{ session_tags : "categorizes"
    chat_sessions ||--o{ attachments : "has files (1:N)"
    chat_messages ||--o| attachments : "associated with (1:1)"

    chat_sessions ||--o{ memories : "produces (1:N)"
    memories ||--o{ memory_versions : "tracks changes (1:N)"
    chat_sessions ||--o{ token_usage_metrics : "logs savings (1:N)"

    chat_sessions {
        UUID id PK
        VARCHAR external_session_id UK "Antigravity/Copilot ID"
        VARCHAR title
        TEXT description
        VARCHAR provider "anthropic/gemini/openai"
        VARCHAR model_name
        VARCHAR project_name "Indexed"
        TEXT workspace_path
        VARCHAR status "ACTIVE/COMPLETED/ARCHIVED"
        JSONB metadata
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
    }

    chat_messages {
        UUID id PK
        UUID session_id FK "References chat_sessions(id)"
        INT sequence_number "Unique per session"
        VARCHAR role "user/assistant/system"
        TEXT content
        TEXT thinking "Model chain-of-thought"
        JSONB tool_calls
        VARCHAR tool_call_id
        INT tokens_used
        TIMESTAMPTZ created_at
        TSVECTOR fts_vector "GIN Indexed FTS"
    }

    tags {
        UUID id PK
        VARCHAR name UK "LOWER(name) Indexed"
        TIMESTAMPTZ created_at
    }

    session_tags {
        UUID session_id PK, FK
        UUID tag_id PK, FK
    }

    attachments {
        UUID id PK
        UUID session_id FK
        UUID message_id FK
        VARCHAR file_name
        VARCHAR content_type
        BIGINT file_size
        TEXT storage_path
        TIMESTAMPTZ created_at
    }

    memories {
        UUID id PK
        VARCHAR project_id "Indexed, Scoped"
        UUID session_id FK "Nullable reference to source session"
        VARCHAR category "DECISION, PROJECT, PREFERENCE, PROCEDURAL, ACTION"
        VARCHAR tier "WORKING, SESSION, LONG_TERM, ARCHIVE"
        TEXT content "FTS Indexed"
        DOUBLE_PRECISION importance_score "0.0 to 1.0"
        INT access_count
        BOOLEAN is_active "Indexed soft-delete"
        TIMESTAMPTZ last_accessed_at
        TIMESTAMPTZ created_at
        TIMESTAMPTZ updated_at
        VECTOR embedding "1536-dim HNSW Cosine Index"
        TSVECTOR fts_vector "GIN Indexed full-text"
    }

    memory_versions {
        UUID id PK
        UUID memory_id FK "References memories(id) ON DELETE CASCADE"
        TEXT previous_content
        VARCHAR change_reason
        INT version_number
        TIMESTAMPTZ created_at
    }

    token_usage_metrics {
        UUID id PK
        VARCHAR project_id "Indexed"
        UUID session_id FK
        VARCHAR model_name
        INT raw_prompt_tokens
        INT optimized_prompt_tokens
        INT tokens_saved
        DOUBLE_PRECISION reduction_percentage
        INT latency_ms
        TIMESTAMPTZ created_at
    }

    context_cache {
        UUID id PK
        VARCHAR query_hash UK "SHA-256 Hash"
        VARCHAR project_id "Indexed"
        TEXT cached_prompt
        INT token_count
        TIMESTAMPTZ expires_at "TTL Indexed"
        TIMESTAMPTZ created_at
    }
```

### 5.1 Database Index Specifications
1. **Full-Text Search (GIN)**:
   - `chat_messages.fts_vector`: GIN index on generated `to_tsvector('english', coalesce(content,'') || ' ' || coalesce(thinking,''))`.
   - `memories.fts_vector`: GIN index on generated `to_tsvector('english', coalesce(content,''))`.
2. **Dense Vector Embeddings (HNSW)**:
   - `memories.embedding`: `CREATE INDEX idx_memories_embedding ON memories USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);`
3. **Idempotent Ingestion Unique Index**:
   - `CREATE UNIQUE INDEX uq_chat_messages_session_seq ON chat_messages (session_id, sequence_number);`
4. **Context Cache & Hash Lookups**:
   - `CREATE UNIQUE INDEX uq_context_cache_hash ON context_cache (query_hash);`
   - `CREATE INDEX idx_context_cache_ttl ON context_cache (expires_at);`

---

## 6. Enterprise CI/CD & Verification Gate Architecture

```mermaid
flowchart LR
    Commit["Git Push Commit"] --> Gate1
    
    subgraph Pipeline["Pre-Deployment Verification Gates (cd.yml)"]
        Gate1["Gate 1: Gitleaks<br/>Secret Scan"] --> Gate2a
        Gate2a["Gate 2a: Test Suite<br/>63 Tests (0 Failures)"] --> Gate2b
        Gate2b["Gate 2b: DB Migration<br/>Flyway V1-V4 on pg16"] --> Gate3
        Gate3["Gate 3: Security Scan<br/>Aqua Trivy CVE Audit"] --> Gate4
        Gate4["Gate 4: Container Smoke<br/>Health + Auth + Token Optimizer"]
    end

    Gate4 -->|100% Pass| Deploy["Production Publish<br/>ghcr.io Registry"]
```

1. **Gate 1 (Gitleaks)**: Scans diffs and commit history for exposed credentials, API keys, or private tokens.
2. **Gate 2a (Unit & Integration Tests)**: Executes all 63 Spring Boot unit and integration tests using clean H2 in-memory profiles.
3. **Gate 2b (PostgreSQL Migration Dry-Run)**: Boots an ephemeral `pgvector/pgvector:pg16` container and runs all Flyway migrations (`V1` through `V4`) to guarantee schema integrity.
4. **Gate 3 (Trivy Container CVE Scan)**: Packages the multi-stage Alpine Docker image and audits against the CVE vulnerability database.
5. **Gate 4 (Live Smoke Test)**: Boots the actual application container, verifies `/actuator/health`, confirms security rejection (`401` on missing key), validates authorized access (`200` with `X-API-KEY`), and tests the Knapsack Token Budget Optimizer.
6. **Production Delivery**: Publishes release containers to GitHub Container Registry (`ghcr.io/livejpsingh/ai-session-vault`) only upon 100% gate pass.

---

## 7. Related Architectural Documents

- 📋 [Phase-Wise Implementation Plan](file:///c:/workspace/public/ai-session-vault/docs/PHASE_WISE_IMPLEMENTATION_PLAN.md)
- ⚙️ [CI/CD Pipeline & Pre-Deployment Verification Gates](file:///c:/workspace/public/ai-session-vault/docs/CI_CD_PIPELINE.md)
- 🏛️ [System Architecture Guide](file:///c:/workspace/public/ai-session-vault/docs/System-Architecture-Guide.md)
- 🛡️ [Production-Readiness Audit Report](file:///c:/workspace/public/ai-session-vault/docs/PRODUCTION_READINESS_REPORT.md)
- 💾 [Backup & Disaster Recovery Guide](file:///c:/workspace/public/ai-session-vault/docs/BACKUP_AND_RECOVERY.md)

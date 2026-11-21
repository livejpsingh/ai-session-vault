# AI Session Vault

**AI Session Vault** is a self-hosted conversation persistence and semantic-memory service for AI agents. Built with Java 21, Spring Boot, PostgreSQL, and pgvector, it ingests session transcripts and provides REST APIs and Model Context Protocol (MCP) tools for full-text and hybrid semantic search, grounded recall, session management, and export. The project also includes API-key authentication, Docker Compose deployment, and backup and recovery tooling.

Use it to keep conversation history available across agent sessions and retrieve relevant context through semantic and keyword search.

---

## 📚 Documentation Index

All architectural, operational, and disaster recovery documentation is organized under the [`docs/`](docs/) directory:

- 📋 **[Phase-Wise Implementation Plan (Vault 2.0)](docs/PHASE_WISE_IMPLEMENTATION_PLAN.md)**: Gap analysis, token optimization engine, hierarchical memory, and Phased implementation roadmap.
- 🏛️ **[System Architecture Guide](docs/System-Architecture-Guide.md)**: RAG ingestion and retrieval flow, provider profiles, vector schema, and operational notes.
- 🧭 **[Core Architecture Reference](docs/ARCHITECTURE.md)**: Existing subsystem design, MCP, search, ingestion, security, domain models, and database schema.
- 🛡️ **[Production-Readiness Audit Report](docs/PRODUCTION_READINESS_REPORT.md)**: 15-dimension architecture and security audit, remediations, test suite execution metrics, and operational limitations.
- 💾 **[Backup & Disaster Recovery Guide](docs/BACKUP_AND_RECOVERY.md)**: Automated logical backup procedures (`pg_dump`), restore runbooks, WAL archiving, and attachment storage sync.

---

## 🚀 Quick Start

### 1. Configure local secrets and the embedding tokenizer

Copy `.env.example` to `.env` and set unique values for `POSTGRES_PASSWORD` and `VAULT_API_KEY`. The local RAG profile uses Ollama and requires `nomic-embed-text`:

```powershell
ollama pull nomic-embed-text
New-Item -ItemType Directory -Force .\models\nomic-embed-text-v1.5
Invoke-WebRequest `
  -Uri "https://huggingface.co/nomic-ai/nomic-embed-text-v1.5/resolve/e9b6763023c676ca8431644204f50c2b100d9aab/vocab.txt?download=true" `
  -OutFile .\models\nomic-embed-text-v1.5\vocab.txt
```

For the cloud profile, set `SPRING_PROFILES_ACTIVE=cloud-rag` and provide `OPENAI_API_KEY` in `.env`. Use an embedding model configured to return the same dimension as `VAULT_RAG_EMBEDDING_DIMENSION`; changing dimensions after the initial Flyway migration requires a schema migration and full reindex.

### 2. Start Services via Docker Compose

```bash
docker compose up -d --build
```

### 3. Verify Health

```bash
curl -f http://localhost:8080/actuator/health
```

### RAG API and MCP tools

- `POST /api/v1/rag/search` — hybrid semantic and keyword search with optional project, agent, tag, and metadata filters.
- `POST /api/v1/rag/reindex/{sessionId}` and `POST /api/v1/rag/reindex-all` — enqueue asynchronous reindexing; both return `202 Accepted`.
- `GET /api/v1/rag/reindex/{sessionId}` — inspect the indexing state for a session.
- MCP tools `semantic_recall` and `find_related_sessions` return grounded excerpts and related-session summaries.

All API and MCP routes require the `X-API-KEY` configured in `.env`; only the documented health/info endpoints are public.

### Automatic chat capture in VS Code (Antigravity & GitHub Copilot)

The workspace includes automated lifecycle hooks that capture 100% of your chat sessions and conversation turns into your local PostgreSQL database (`ai_session_vault`):

- **Google Antigravity:** Lifecycle hooks in [`.agents/hooks.json`](.agents/hooks.json) hook into `SessionStart` and `Stop` events.
- **GitHub Copilot:** Lifecycle hooks in [`.github/hooks/ai-session-vault.json`](.github/hooks/ai-session-vault.json) hook into `SessionStart` and `Stop` events.
- **1-Click Hook Installer:**
  ```powershell
  # Windows
  .\scripts\install-hooks.ps1

  # Linux / macOS
  bash scripts/install-hooks.sh
  ```

**How it works during everyday development:**
1. When you open VS Code and start a conversation in GitHub Copilot or Antigravity, the `SessionStart` hook registers the session in `chat_sessions`.
2. As you chat, on each turn completion (`Stop` event), the hook extracts transcripts or message history and posts it to `POST /api/transcripts/ingest`.
3. Spring Boot sanitizes null bytes, deduplicates turns, and persists all prompts, reasoning traces (`thinking`), and assistant responses into `chat_messages`.
4. The pgvector hybrid RAG engine and Token Budget Optimization Engine automatically index and prepare high-density memories, slashing token consumption by 70% to 90% on subsequent turns.
5. All chat data remains strictly local and private in your self-hosted PostgreSQL database.

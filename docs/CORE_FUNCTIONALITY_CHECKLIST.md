# Core Functionality Guardrails & Pre-Improvement Checklist

> **Mandatory Check:** Before designing, implementing, or merging **any** improvement, refactoring, or feature in **AI Session Vault**, verify that every core invariant below remains intact.

---

## 1. Non-Negotiable Core Objective

**AI Session Vault** is an **always-on background service** that automatically captures and persists AI coding chat sessions into **PostgreSQL + `pgvector`** across **any repository** and **any IDE** (**VS Code**, **IntelliJ IDEA**, **Android Studio**, etc.) for **any AI assistant** (**Google Antigravity**, **GitHub Copilot**, **Claude**, **Codex**, **Cursor**).

Secondary features (memory extraction, Hybrid RAG, token budget optimization, observability) **must never** break, disable, or complicate this core persistence loop.

---

## 2. Pre-Change Verification Checklist

Before starting any improvement, confirm your change preserves all 5 pillars:

### Pillar 1: Always-On Background Service & Auto-Start
- [ ] **Machine Login Auto-Start:** `scripts/start-vault-background.ps1` and the Windows `HKCU:\Software\Microsoft\Windows\CurrentVersion\Run\AiSessionVault` entry continue to launch `target/ai-session-vault-0.0.1-SNAPSHOT.jar` cleanly on port `8080`.
- [ ] **VS Code Auto-Start:** `.vscode/tasks.json` retains `"runOptions": { "runOn": "folderOpen" }` on `AI Session Vault: Start Local Server`.
- [ ] **JAR Freshness:** Whenever Java source or `application*.yml` changes, rebuild `target/ai-session-vault-0.0.1-SNAPSHOT.jar` (`.\mvnw.cmd package -DskipTests --batch-mode`) so the background auto-start service runs the latest code.

### Pillar 2: Universal Multi-IDE & Multi-Agent Auto-Capture
- [ ] **Lifecycle Hooks:** `.agents/hooks.json`, `.github/hooks/ai-session-vault.json`, `scripts/session-vault-hook.ps1`, `scripts/session-vault-hook.sh`, `.agents/hooks/session-start.ps1`, and `.agents/hooks/session-stop.ps1` remain functional without requiring manual user intervention.
- [ ] **Environment & `.env` Fallback:** Hook scripts continue to resolve `VAULT_API_KEY` from the process environment **or** `.env` so background/IDE-spawned hooks never silently fail when `VAULT_API_KEY` is only defined in `.env`.
- [ ] **Multi-Provider Support:** `MessageRole` and `TranscriptIngestionService` continue to accept transcripts from Google Antigravity, GitHub Copilot, Claude, Codex, and Cursor across any workspace path.

### Pillar 3: Durable PostgreSQL + `pgvector` Persistence
- [ ] **Immutable Raw Archive:** Raw chat turns in `chat_sessions` and `chat_messages` are never deleted or overwritten during memory consolidation or optimization.
- [ ] **Graceful `pgvector` Compatibility:** Flyway migrations and startup continue to succeed on PostgreSQL both **with** `pgvector` enabled and **without** `pgvector` installed locally (conditional DDL in `V3`/`V4`).
- [ ] **Additive Migrations Only:** Never edit existing applied Flyway migrations (`V1`–`V5`); add incremental migrations (`V6__...`).

### Pillar 4: Core Session & MCP Contracts
- [ ] **REST Endpoints:** `POST /api/sessions`, `POST /api/sessions/{sessionId}/messages`, `POST /api/transcripts/ingest`, `GET /api/sessions`, and `GET /api/search` remain backward-compatible.
- [ ] **MCP Tools:** Original session tools (`vault_save_message`, `vault_ingest_transcript`, `vault_get_session`, `vault_list_sessions`, `vault_search_messages`, `vault_export_session`) and `memory_*` tools remain registered on `/mcp`.
- [ ] **Health Endpoint:** `GET /actuator/health` remains publicly accessible without `X-API-KEY` so hooks and monitoring probes can check service readiness.

### Pillar 5: Security & Verification Gate
- [ ] **No Hardcoded Secrets in Examples:** Keep examples, docs, and configs parameterized via `VAULT_API_KEY`.
- [ ] **Full Test & Benchmark Pass:** Run `.\mvnw.cmd test jacoco:report --batch-mode` (including `PerformanceAndReductionBenchmarkTest`) and confirm zero failures before completing any change.
- [ ] **Live Smoke Check:** If the service is running locally, verify `http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

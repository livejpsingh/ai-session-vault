# AI Session Vault

**AI Session Vault** is an enterprise-grade conversation memory server, ingestion pipeline, and audit platform designed for autonomous AI agents (such as Antigravity, Claude, and Gemini developer agents) and developer toolchains.

It provides durable conversation storage, sub-second full-text search, transcript ingestion, markdown/JSON export, and native Model Context Protocol (MCP) server endpoints over HTTP SSE.

---

## 📚 Documentation Index

All architectural, operational, and disaster recovery documentation is organized under the [`docs/`](docs/) directory:

- 🏛️ **[System Architecture Guide](docs/ARCHITECTURE.md)**: End-to-end architecture design, Mermaid diagrams, subsystem breakdowns (MCP, Search, Ingestion, Security), domain models, database schema, and performance optimizations.
- 🛡️ **[Production-Readiness Audit Report](docs/PRODUCTION_READINESS_REPORT.md)**: 15-dimension architecture and security audit, remediations, test suite execution metrics, and operational limitations.
- 💾 **[Backup & Disaster Recovery Guide](docs/BACKUP_AND_RECOVERY.md)**: Automated logical backup procedures (`pg_dump`), restore runbooks, WAL archiving, and attachment storage sync.

---

## 🚀 Quick Start

### 1. Start Services via Docker Compose
```bash
docker compose up -d
```

### 2. Verify Health
```bash
curl -f http://localhost:8080/actuator/health
```

### 3. Authenticate with API Key
Default key: `vault-secret-key-2026` (override via `VAULT_API_KEY` environment variable).
```bash
curl -H "X-API-KEY: vault-secret-key-2026" http://localhost:8080/api/sessions
```

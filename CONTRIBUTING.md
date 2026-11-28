# Contributing to AI Session Vault

Thank you for your interest in contributing to **AI Session Vault**! We welcome bug reports, feature proposals, documentation improvements, benchmark suites, and pull requests.

## Ways to Contribute

- **Questions & Architecture Ideas:** Start a thread in [GitHub Discussions](https://github.com/livejpsingh/ai-session-vault/discussions).
- **Bug Reports:** Open an issue using the [Bug Report template](.github/ISSUE_TEMPLATE/bug_report.yml).
- **Feature Requests:** Open an issue using the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.yml).
- **Code & Documentation PRs:** Fork the repository, create a feature branch, and submit a pull request.

---

## Development Prerequisites

- **Java 25 LTS** (Temurin or OpenJDK 25)
- **Docker Desktop** (for `pgvector/pgvector:pg16` and Testcontainers integration tests)
- **Maven Wrapper** (`./mvnw` on Linux/macOS, `.\mvnw.cmd` on Windows)

---

## Local Setup

1. **Fork and clone** the repository:
   ```bash
   git clone https://github.com/<your-username>/ai-session-vault.git
   cd ai-session-vault
   ```

2. **Configure environment variables** (never commit `.env` or real secrets):
   ```bash
   cp .env.example .env
   ```
   Edit `.env` and set strong local values for `POSTGRES_PASSWORD` and `VAULT_API_KEY`.

3. **Run the unit test & benchmark suite:**
   ```bash
   # Linux / macOS
   ./mvnw test

   # Windows PowerShell
   .\mvnw.cmd test
   ```

4. **Generate JaCoCo test coverage report:**
   ```bash
   ./mvnw test jacoco:report
   # Report output: target/site/jacoco/index.html
   ```

---

## Engineering & Review Standards

> **Mandatory Pre-Change Check:** Before implementing any improvement or refactor, review [docs/CORE_FUNCTIONALITY_CHECKLIST.md](docs/CORE_FUNCTIONALITY_CHECKLIST.md) to ensure always-on background auto-start, multi-IDE/multi-agent session capture, and PostgreSQL + `pgvector` persistence remain intact.

### 1. Security & Secret Hygiene
- **Never commit hardcoded API keys, tokens, or passwords** in code, examples, Postman collections, or documentation.
- All examples and scripts must reference environment variables (`${VAULT_API_KEY}`).
- CI runs **Gitleaks** on every push and pull request.

### 2. Database Migrations (Flyway)
- **Never modify existing applied migrations** (`V1` through `V5`) in `src/main/resources/db/migration/`.
- Add new schema changes as incremental migrations (`V6__<description>.sql`, etc.) and verify with a clean PostgreSQL + `pgvector` container.

### 3. Testing & Benchmarks
- New services, controllers, and MCP tools must include unit tests (JUnit 5 + Mockito/AssertJ) and, where applicable, Testcontainers integration tests.
- Claims around token reduction, budget enforcement, or latency should be backed by reproducible assertions in `src/test/java/com/jitendra/ai/sessionvault/benchmark/`.

### 4. Pull Request Process
1. Branch from `main` using `feature/<short-name>`, `fix/<short-name>`, or `docs/<short-name>`.
2. Ensure `./mvnw test jacoco:report` passes with zero failures.
3. Keep PRs focused and include a clear summary of changes, motivation, and verification steps.
4. Link any related GitHub Issue or Discussion in the PR description.

---

## Code of Conduct

Be respectful, constructive, and collaborative. Focus feedback on technical merit, maintainability, and clarity.

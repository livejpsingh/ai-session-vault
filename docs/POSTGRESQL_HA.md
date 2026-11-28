# PostgreSQL High Availability (HA) Patterns

AI Session Vault stores all conversational transcripts (`chat_messages`), structured memories (`memories`), embeddings (`pgvector`), and transactional outbox state (`outbox_events`) in PostgreSQL. For production deployments requiring high uptime and zero data loss, use one of the HA architectures below.

---

## 1. Recommended Production Topologies

### Option A: Primary + Streaming Replica with Patroni (VM / Bare Metal)
- **Architecture:** 1 Primary + 2 Synchronous/Asynchronous Standby Replicas managed by **Patroni** with **etcd** (or Consul) for distributed consensus and automatic leader election.
- **Replication Settings (`postgresql.conf`):**
  ```ini
  wal_level = replica
  max_wal_senders = 10
  max_replication_slots = 10
  synchronous_commit = on
  synchronous_standby_names = 'FIRST 1 (standby1, standby2)'
  hot_standby = on
  ```
- **Failover Target:** Automated failover in `< 30 seconds` with zero committed transaction loss when `synchronous_commit = on`.

### Option B: Kubernetes with CloudNativePG (CNPG)
If deploying on Kubernetes, **CloudNativePG** natively manages PostgreSQL + `pgvector` clusters, automated failover, and continuous WAL archiving to S3/GCS:

```yaml
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: ai-session-vault-pg
spec:
  instances: 3
  imageName: ghcr.io/cloudnative-pg/postgresql:16.4
  postgresql:
    parameters:
      shared_buffers: "1GB"
      maintenance_work_mem: "512MB"
      hnsw.ef_search: "100"
  bootstrap:
    initdb:
      database: ai_session_vault
      owner: vault_user
      postInitSQL:
        - CREATE EXTENSION IF NOT EXISTS vector;
        - CREATE EXTENSION IF NOT EXISTS pg_trgm;
  storage:
    size: 50Gi
```

---

## 2. Connection Pooling & Spring Boot Tuning

Place **PgBouncer** (in `transaction` pooling mode) in front of the PostgreSQL primary, and tune Spring Boot's HikariCP pool in `application.yml` or environment variables:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://pgbouncer-primary:6432/ai_session_vault?prepareThreshold=0
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 5000
      max-lifetime: 1800000
      keepalive-time: 300000
```

> [!NOTE]
> Set `prepareThreshold=0` on the JDBC URL when using PgBouncer in `transaction` pooling mode to prevent prepared-statement collisions across pooled connections.

---

## 3. `pgvector` HNSW Index & Replication Considerations

1. **WAL Logging for HNSW:** `pgvector` 0.5+ writes HNSW index mutations to WAL, meaning streaming replicas automatically stay in sync with vector inserts and updates.
2. **Memory Sizing:** Size `maintenance_work_mem` (e.g., `512MB`–`2GB`) during index builds and `shared_buffers` (25% of RAM) so hot HNSW graph layers remain resident in memory.
3. **Read-Replica Offloading (Optional):** Heavy analytical queries (`token_usage_metrics`, `memory_explain_retrieval`) can be routed to a read-only standby replica, while `OutboxEventWorker` (`SELECT ... FOR UPDATE SKIP LOCKED`) and writes **must** target the primary.

---

## 4. Continuous Backup & Point-in-Time Recovery (PITR)

Pair HA replication with continuous WAL archiving (`pgBackRest` or `WAL-G`) to object storage so you can recover from logical errors (e.g., accidental bulk deletes) independently of replica failover. See [Backup & Disaster Recovery Guide](BACKUP_AND_RECOVERY.md) for restore runbooks.

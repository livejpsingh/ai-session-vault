# AI Session Vault - Backup & Disaster Recovery Guide

## 1. Overview & Objectives
AI Session Vault stores mission-critical developer sessions, tool execution logs, reasoning traces, and audit metadata. This document outlines the backup architecture, automated retention policies, Point-In-Time Recovery (PITR), and disaster recovery (DR) runbooks.

### Targets
- **Recovery Point Objective (RPO):** < 1 hour (daily full backup + continuous WAL archiving in production)
- **Recovery Time Objective (RTO):** < 15 minutes

---

## 2. Backup Strategy

### Automated Logical Backups (`pg_dump`)
- Full daily logical dumps compressed with gzip.
- Executed via `scripts/backup.sh` (Linux) or `scripts/backup.ps1` (Windows/PowerShell).
- Default backup retention: 14 days (configurable via `RETENTION_DAYS`).

### Physical Backups & WAL Archiving (Production)
For high-availability clusters:
1. Enable `wal_level = replica` in PostgreSQL configuration.
2. Configure continuous WAL archiving to object storage (e.g., S3/GCS) using tools like `pgBackRest` or `WAL-G`.
3. Perform weekly full physical base backups (`pg_basebackup`).

---

## 3. Backup Execution

### Standalone / Local Execution
```bash
# Set credentials
export SPRING_DATASOURCE_PASSWORD="vault_password"

# Run backup script
./scripts/backup.sh
```

### Docker Compose Execution
```bash
docker exec -t ai-session-vault-db pg_dump -U vault_user -d ai_session_vault | gzip > ./backups/vault_backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

### Windows Execution
```powershell
.\scripts\backup.ps1 -BackupDir "./backups" -RetentionDays 14
```

---

## 4. Disaster Recovery & Restore Runbook

### Step 1: Prepare Database Environment
If restoring from scratch or after data corruption, ensure the PostgreSQL service is running:
```bash
docker compose up -d postgres
```

### Step 2: Execute Restore
```bash
# Decompress and apply SQL dump
./scripts/restore.sh ./backups/vault_backup_YYYYMMDD_HHMMSS.sql.gz
```

### Step 3: Run Flyway Migration Verification
Start the application or execute Flyway validate to ensure schema consistency:
```bash
./mvnw flyway:info
```

### Step 4: Health Check & Smoke Test
Verify application health and query counts:
```bash
curl -f http://localhost:8080/actuator/health
curl -H "X-API-KEY: ${VAULT_API_KEY:?Set VAULT_API_KEY}" http://localhost:8080/api/sessions
```

---

## 5. Storage & File Attachments Backup
In addition to the PostgreSQL database, attachments stored in `${VAULT_ATTACHMENTS_DIR}` (default `./vault-storage/attachments`) must be backed up concurrently:
```bash
rsync -avz --delete ./vault-storage/ /backup-storage/vault-attachments/
```

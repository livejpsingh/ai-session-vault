#!/usr/bin/env bash
# AI Session Vault - Automated Database Backup Script
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-./backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/vault_backup_${TIMESTAMP}.sql.gz"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

DB_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
DB_PORT="${SPRING_DATASOURCE_PORT:-5432}"
DB_NAME="${SPRING_DATASOURCE_DB:-ai_session_vault}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-vault_user}"
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-vault_password}"

mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting backup of ${DB_NAME} to ${BACKUP_FILE}..."

# Perform pg_dump with compression
pg_dump -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  --format=plain --no-owner --no-privileges | gzip > "${BACKUP_FILE}"

FILESIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup successful! File size: ${FILESIZE}"

# Prune old backups older than RETENTION_DAYS
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Purging backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -name "vault_backup_*.sql.gz" -type f -mtime "+${RETENTION_DAYS}" -delete

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Backup and retention rotation completed."

#!/usr/bin/env bash
# AI Session Vault - Database Restore Script
set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <backup-file.sql.gz>"
    exit 1
fi

BACKUP_FILE="$1"
DB_HOST="${SPRING_DATASOURCE_HOST:-localhost}"
DB_PORT="${SPRING_DATASOURCE_PORT:-5432}"
DB_NAME="${SPRING_DATASOURCE_DB:-ai_session_vault}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-vault_user}"
export PGPASSWORD="${SPRING_DATASOURCE_PASSWORD:-vault_password}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "Error: Backup file does not exist: ${BACKUP_FILE}"
    exit 1
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Restoring ${DB_NAME} from ${BACKUP_FILE}..."

if [[ "${BACKUP_FILE}" == *.gz ]]; then
    gzip -dc "${BACKUP_FILE}" | psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}"
else
    psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -f "${BACKUP_FILE}"
fi

echo "[$(date '+%Y-%m-%d %H:%M:%S')] Database restore completed successfully."

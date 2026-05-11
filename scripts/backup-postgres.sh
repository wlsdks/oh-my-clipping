#!/bin/bash
# ============================================================
# PostgreSQL 백업 스크립트
# 사용법: ./docker/backup-postgres.sh [백업 디렉터리]
# 크론 예: 0 2 * * * /path/to/docker/backup-postgres.sh >> /var/log/backup.log 2>&1
# ============================================================

set -euo pipefail

BACKUP_DIR="${1:-./backups}"
CONTAINER_NAME="clipping-postgres"
DB_NAME="clipping"
DB_USER="clipping_admin"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/clipping_${DATE}.sql.gz"
RETENTION_DAYS=7

mkdir -p "$BACKUP_DIR"

echo "[$(date)] Starting PostgreSQL backup..."

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[$(date)] ERROR: Container '${CONTAINER_NAME}' is not running"
    exit 1
fi

docker exec "$CONTAINER_NAME" pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"

SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "[$(date)] Backup complete: ${BACKUP_FILE} (${SIZE})"

# 오래된 백업 삭제
DELETED=$(find "$BACKUP_DIR" -name "clipping_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete -print | wc -l)
if [ "$DELETED" -gt 0 ]; then
    echo "[$(date)] Deleted ${DELETED} backup(s) older than ${RETENTION_DAYS} days"
fi

echo "[$(date)] Done."

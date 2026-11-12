#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
[[ -f .env ]] || { echo "ERROR: .env is missing." >&2; exit 1; }
BACKUP_DIR="${BACKUP_DIR:-backups}"
mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
BACKUP_FILE="${BACKUP_DIR}/payment_platform-${TIMESTAMP}.dump"
umask 077
echo "Creating PostgreSQL custom-format backup..."
docker compose --env-file .env exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$BACKUP_FILE"
[[ -s "$BACKUP_FILE" ]] || { rm -f "$BACKUP_FILE"; echo "ERROR: backup is empty." >&2; exit 1; }
docker compose --env-file .env exec -T postgres pg_restore -l - < "$BACKUP_FILE" >/dev/null
chmod 600 "$BACKUP_FILE"
echo "Backup archive verified: $BACKUP_FILE"
echo "Copy an encrypted backup off-host before a real production release."

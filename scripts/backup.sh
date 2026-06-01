#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKUP_DIR="$ROOT/backups"
DATE=$(date +%Y-%m-%d)
BACKUP_FILE="$BACKUP_DIR/backup-$DATE.tar.gz"
TMP_DIR=$(mktemp -d)

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

mkdir -p "$BACKUP_DIR"

log() { echo "[backup] $*"; }

log "Starting backup for $DATE..."

log "Dumping PostgreSQL..."
docker exec pravoos-postgres pg_dumpall -U postgres > "$TMP_DIR/postgres.sql"

log "Dumping MongoDB..."
docker exec pravoos-mongodb sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive' > "$TMP_DIR/mongo.archive"

log "Compressing..."
tar -czf "$BACKUP_FILE" -C "$TMP_DIR" .

BACKUP_SIZE=$(du -sh "$BACKUP_FILE" | cut -f1)
log "Backup saved: $BACKUP_FILE ($BACKUP_SIZE)"

# Keep last 30 days
find "$BACKUP_DIR" -name "backup-*.tar.gz" -mtime +30 -delete
log "Old backups cleaned (kept last 30 days)"

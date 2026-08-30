#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"
BACKUP_DIR="$ROOT/backups"
DATE=$(date +%Y-%m-%d)
BACKUP_FILE="$BACKUP_DIR/backup-$DATE.tar.gz"
ENCRYPTED_FILE="$BACKUP_FILE.gpg"
TMP_DIR=$(mktemp -d)

cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

mkdir -p "$BACKUP_DIR"

log() { echo "[backup] $*"; }

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source <(grep -E '^(BACKUP_ENCRYPTION_KEY|DOCUMENT_STORAGE_PATH)=' "$ENV_FILE")
    set +a
fi

DOCUMENT_STORAGE_ROOT="${DOCUMENT_STORAGE_PATH:-/app/documents}"
DOCUMENT_STORAGE_PARENT="$(dirname "$DOCUMENT_STORAGE_ROOT")"
DOCUMENT_STORAGE_NAME="$(basename "$DOCUMENT_STORAGE_ROOT")"

: "${BACKUP_ENCRYPTION_KEY:?BACKUP_ENCRYPTION_KEY not set — refusing to write an unencrypted backup with client data}"

log "Starting backup for $DATE..."

log "Dumping PostgreSQL..."
docker exec pravoos-postgres pg_dumpall -U postgres > "$TMP_DIR/postgres.sql"

log "Dumping MongoDB..."
docker exec pravoos-mongodb sh -c 'mongodump --username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin --archive' > "$TMP_DIR/mongo.archive"

log "Archiving document files (documents_data volume)..."
if ! docker exec pravoos-ai-service tar -cf - -C "$DOCUMENT_STORAGE_PARENT" "$DOCUMENT_STORAGE_NAME" > "$TMP_DIR/documents.tar"; then
    log "FAILED to archive $DOCUMENT_STORAGE_ROOT from pravoos-ai-service — refusing to write a backup without document files"
    exit 1
fi
DOCUMENTS_COUNT=$(tar -tf "$TMP_DIR/documents.tar" | grep -vc '/$' || true)
log "Document files archived: $DOCUMENTS_COUNT"

log "Compressing..."
tar -czf "$TMP_DIR/backup.tar.gz" -C "$TMP_DIR" postgres.sql mongo.archive documents.tar

log "Encrypting (AES-256)..."
gpg --batch --yes --symmetric --cipher-algo AES256 \
    --passphrase "$BACKUP_ENCRYPTION_KEY" \
    --output "$ENCRYPTED_FILE" \
    "$TMP_DIR/backup.tar.gz"

BACKUP_SIZE=$(du -sh "$ENCRYPTED_FILE" | cut -f1)
log "Encrypted backup saved: $ENCRYPTED_FILE ($BACKUP_SIZE)"

# Keep last 30 days
find "$BACKUP_DIR" -name "backup-*.tar.gz.gpg" -mtime +30 -delete
log "Old backups cleaned (kept last 30 days)"

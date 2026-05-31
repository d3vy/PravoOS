#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="$ROOT/.env"

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source <(grep -E '^TELEGRAM_' "$ENV_FILE")
    set +a
fi

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN not set}"
: "${TELEGRAM_ADMIN_CHAT_ID:?TELEGRAM_ADMIN_CHAT_ID not set}"

BACKUP_DIR="$ROOT/backups"
LATEST=$(ls -t "$BACKUP_DIR"/backup-*.tar.gz 2>/dev/null | head -1)

if [ -z "$LATEST" ]; then
    echo "[backup-send] No backup file found in $BACKUP_DIR"
    exit 1
fi

BACKUP_SIZE=$(du -sh "$LATEST" | cut -f1)
FILENAME=$(basename "$LATEST")
DATE=$(date +%Y-%m-%d)

MAX_SIZE_MB=50
ACTUAL_SIZE_MB=$(du -m "$LATEST" | cut -f1)
if [ "$ACTUAL_SIZE_MB" -gt "$MAX_SIZE_MB" ]; then
    curl -sf \
        -d "chat_id=$TELEGRAM_ADMIN_CHAT_ID" \
        -d "text=⚠️ PravoOS backup $DATE ($BACKUP_SIZE) — файл слишком большой для Telegram (>50MB). Скачай вручную с сервера: backups/$FILENAME" \
        "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage"
    echo "[backup-send] File too large for Telegram ($ACTUAL_SIZE_MB MB), sent text notification"
    exit 0
fi

curl -sf \
    -F "chat_id=$TELEGRAM_ADMIN_CHAT_ID" \
    -F "document=@$LATEST" \
    -F "caption=🗄 PravoOS backup $DATE | $BACKUP_SIZE" \
    "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendDocument"

echo "[backup-send] Sent $FILENAME ($BACKUP_SIZE) to Telegram"

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPTS_DIR="$ROOT/scripts"
ENV_FILE="$ROOT/.env"

CRAWL_CONFIGS=(
    "sites.yaml:ingest_state_bankruptcy.json"
    "practice_extra.yaml:ingest_state_plenums.json"
    "plenums.yaml:ingest_state_plenums2.json"
    "law127.yaml:ingest_state_127fz.json"
    "law218.yaml:ingest_state_218fz.json"
)

BASE_URL=""
ONLY=""
DRY_RUN=0
SOURCES_DIR="$SCRIPTS_DIR/practice_sources"
URLS_FILE="$SCRIPTS_DIR/urls.txt"
PYTHON_BIN="${PYTHON_BIN:-python3}"

log() { echo "[seed] $*"; }
fail() { echo "[seed] $*" >&2; exit 1; }

usage() {
    cat <<'USAGE'
Наполнение общей базы знаний (документы без case_id).

  ./scripts/seed-knowledge-base.sh [опции]

  --base-url URL     API через api-gateway (по умолчанию FRONTEND_BASE_URL из .env)
  --only CONFIG      прогнать только один конфиг, например --only plenums.yaml
  --list             показать конфиги и их state-файлы, ничего не запуская
  --dry-run          краулер обходит источники, но не загружает документы
  -h, --help         эта справка

Креды ADMIN берутся из .env (ADMIN_EMAIL / ADMIN_PASSWORD).
Скрипт намеренно не вызывается из deploy.sh: он ходит по внешним сайтам.
Повторный запуск инкрементален — уже загруженные документы пропускаются по state-файлу.
USAGE
}

while [ $# -gt 0 ]; do
    case "$1" in
        --base-url) BASE_URL="${2:?--base-url требует значение}"; shift 2 ;;
        --only) ONLY="${2:?--only требует значение}"; shift 2 ;;
        --dry-run) DRY_RUN=1; shift ;;
        --list)
            for pair in "${CRAWL_CONFIGS[@]}"; do
                echo "  ${pair%%:*}  ->  ${pair##*:}"
            done
            exit 0 ;;
        -h|--help) usage; exit 0 ;;
        *) fail "Неизвестный аргумент: $1" ;;
    esac
done

if [ -f "$ENV_FILE" ]; then
    set -a
    # shellcheck disable=SC1090
    source <(grep -E '^(ADMIN_EMAIL|ADMIN_PASSWORD|FRONTEND_BASE_URL)=' "$ENV_FILE")
    set +a
fi

: "${ADMIN_EMAIL:?ADMIN_EMAIL не задан — добавь в .env или экспортируй}"
: "${ADMIN_PASSWORD:?ADMIN_PASSWORD не задан — добавь в .env или экспортируй}"

if [ -z "$BASE_URL" ]; then
    BASE_URL="${FRONTEND_BASE_URL:-http://localhost:8080}"
fi
BASE_URL="${BASE_URL%/}"

command -v "$PYTHON_BIN" >/dev/null 2>&1 || fail "$PYTHON_BIN не найден"
"$PYTHON_BIN" - <<'PY' || fail "Нет зависимостей: $PYTHON_BIN -m pip install -r scripts/requirements.txt"
import importlib.util, sys
missing = [m for m in ("requests", "yaml", "bs4", "pypdf") if not importlib.util.find_spec(m)]
sys.exit(1 if missing else 0)
PY

log "Цель: $BASE_URL (admin: $ADMIN_EMAIL)"
[ "$DRY_RUN" -eq 1 ] && log "Режим dry-run: документы не загружаются"

processed=0
failed_configs=()

for pair in "${CRAWL_CONFIGS[@]}"; do
    config="${pair%%:*}"
    state="${pair##*:}"
    [ -n "$ONLY" ] && [ "$ONLY" != "$config" ] && continue
    [ -f "$SCRIPTS_DIR/$config" ] || fail "Конфиг не найден: $SCRIPTS_DIR/$config"

    log "--- $config (state: $state)"
    crawl_args=(
        "$SCRIPTS_DIR/crawl_practice.py"
        --config "$SCRIPTS_DIR/$config"
        --state "$SCRIPTS_DIR/$state"
        --base-url "$BASE_URL"
    )
    if [ "$DRY_RUN" -eq 1 ]; then
        crawl_args+=(--dry-run)
    else
        crawl_args+=(--email "$ADMIN_EMAIL" --password "$ADMIN_PASSWORD")
    fi

    if "$PYTHON_BIN" "${crawl_args[@]}"; then
        processed=$((processed + 1))
    else
        log "Конфиг $config завершился с ошибкой — продолжаю остальные"
        failed_configs+=("$config")
    fi
done

if [ -n "$ONLY" ] && [ "$processed" -eq 0 ] && [ ${#failed_configs[@]} -eq 0 ]; then
    fail "Конфиг $ONLY отсутствует в списке — посмотри ./scripts/seed-knowledge-base.sh --list"
fi

if [ -z "$ONLY" ] && [ "$DRY_RUN" -eq 0 ]; then
    if [ -d "$SOURCES_DIR" ] || [ -f "$URLS_FILE" ]; then
        log "--- ручные источники (ingest_practice.py)"
        ingest_args=(
            "$SCRIPTS_DIR/ingest_practice.py"
            --base-url "$BASE_URL"
            --email "$ADMIN_EMAIL" --password "$ADMIN_PASSWORD"
        )
        [ -d "$SOURCES_DIR" ] && ingest_args+=(--sources-dir "$SOURCES_DIR")
        [ -f "$URLS_FILE" ] && ingest_args+=(--urls "$URLS_FILE")
        "$PYTHON_BIN" "${ingest_args[@]}" || failed_configs+=("ingest_practice.py")
    else
        log "Ручных источников нет: ни $SOURCES_DIR, ни $URLS_FILE"
    fi
fi

if [ ${#failed_configs[@]} -gt 0 ]; then
    fail "Завершено с ошибками: ${failed_configs[*]}"
fi

log "Готово. Проверить итог: SELECT count(*) FROM documents WHERE case_id IS NULL AND deleted_at IS NULL;"

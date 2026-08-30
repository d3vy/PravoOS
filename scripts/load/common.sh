#!/usr/bin/env bash
# Общая обвязка нагрузочных скриптов: пути, docker compose, чтение профиля.

set -euo pipefail

LOAD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${LOAD_DIR}/../.." && pwd)"
LOAD_ENV_FILE="${LOAD_ENV_FILE:-${LOAD_DIR}/.env.load}"

if [[ ! -f "${LOAD_ENV_FILE}" ]]; then
  echo "Нет профиля стенда: ${LOAD_ENV_FILE}" >&2
  echo "Скопируй scripts/load/.env.load.example и поправь под свой стенд." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${LOAD_ENV_FILE}"
set +a

if [[ -f "${REPO_ROOT}/.env" ]]; then
  DB_USERNAME="$(grep -E '^DB_USERNAME=' "${REPO_ROOT}/.env" | tail -1 | cut -d= -f2-)"
  MONGO_USERNAME="$(grep -E '^MONGO_USERNAME=' "${REPO_ROOT}/.env" | tail -1 | cut -d= -f2-)"
  MONGO_PASSWORD="$(grep -E '^MONGO_PASSWORD=' "${REPO_ROOT}/.env" | tail -1 | cut -d= -f2-)"
  REDIS_PASSWORD="$(grep -E '^REDIS_PASSWORD=' "${REPO_ROOT}/.env" | tail -1 | cut -d= -f2-)"
fi

DB_USERNAME="${DB_USERNAME:-pravoos}"
MONGO_USERNAME="${MONGO_USERNAME:-}"
MONGO_PASSWORD="${MONGO_PASSWORD:-}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"

compose() {
  (cd "${REPO_ROOT}" && docker compose \
    -f docker-compose.yml \
    -f scripts/load/docker-compose.load.yml \
    "$@")
}

require_stack_up() {
  local missing=""
  for service in "$@"; do
    if [[ -z "$(compose ps -q "${service}" 2>/dev/null)" ]]; then
      missing="${missing} ${service}"
    fi
  done
  if [[ -n "${missing}" ]]; then
    echo "Не подняты сервисы:${missing}" >&2
    echo "Подними стенд: scripts/load/stack.sh up" >&2
    exit 1
  fi
}

log() {
  printf '\033[1;34m[load]\033[0m %s\n' "$*"
}

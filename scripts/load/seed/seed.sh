#!/usr/bin/env bash
# Наполняет стенд реалистичным объёмом: пользователи и практика в Postgres,
# переписки в Mongo. Идемпотентен — повторный запуск пересоздаёт нагрузочные данные.

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/common.sh"

SEED_DIR="${LOAD_DIR}/seed"

require_stack_up postgres mongodb

password_hash() {
  if command -v htpasswd >/dev/null 2>&1; then
    htpasswd -bnBC 12 "" "${LOAD_USER_PASSWORD}" | tr -d ':\n'
  else
    docker run --rm httpd:2.4-alpine htpasswd -bnBC 12 "" "${LOAD_USER_PASSWORD}" | tr -d ':\n'
  fi
}

psql_seed() {
  local database="$1"
  shift
  compose exec -T postgres psql -q -v ON_ERROR_STOP=1 -U "${DB_USERNAME}" -d "${database}" "$@"
}

log "Пользователи: ${LOAD_LAWYERS} юристов в ${LOAD_ORGS} организациях"
HASH="$(password_hash)"
psql_seed pravoos_users \
  -v lawyers="${LOAD_LAWYERS}" \
  -v orgs="${LOAD_ORGS}" \
  -v password_hash="${HASH}" \
  < "${SEED_DIR}/users.sql"

log "Практика: дела, задачи, клиенты, документы, счета"
psql_seed pravoos_ai \
  -v lawyers="${LOAD_LAWYERS}" \
  -v orgs="${LOAD_ORGS}" \
  -v clients_per_lawyer="${LOAD_CLIENTS_PER_LAWYER}" \
  -v cases_per_lawyer="${LOAD_CASES_PER_LAWYER}" \
  -v tasks_per_case="${LOAD_TASKS_PER_CASE}" \
  -v docs_per_case="${LOAD_DOCS_PER_CASE}" \
  -v chunks_per_doc="${LOAD_CHUNKS_PER_DOC}" \
  -v invoices_per_lawyer="${LOAD_INVOICES_PER_LAWYER}" \
  -v time_entries_per_case="${LOAD_TIME_ENTRIES_PER_CASE}" \
  < "${SEED_DIR}/practice.sql"

log "Переписки в Mongo"
LAWYER_IDS="$(compose exec -T postgres psql -tAq -U "${DB_USERNAME}" -d pravoos_users \
  -c "SELECT string_agg('\"' || id || '\"', ',' ORDER BY email) FROM users WHERE email LIKE 'load-lawyer-%@load.pravoos.test'")"
ORG_IDS="$(compose exec -T postgres psql -tAq -U "${DB_USERNAME}" -d pravoos_users \
  -c "SELECT string_agg('\"' || id || '\"', ',' ORDER BY name) FROM organizations WHERE name LIKE 'Нагрузочная коллегия%'")"

MONGO_ARGS=(--quiet)
if [[ -n "${MONGO_USERNAME}" ]]; then
  MONGO_ARGS+=(-u "${MONGO_USERNAME}" -p "${MONGO_PASSWORD}" --authenticationDatabase admin)
fi

compose exec -T mongodb mongosh "${MONGO_ARGS[@]}" \
  --eval "var LAWYERS=${LOAD_LAWYERS}; var ORGS=${LOAD_ORGS}; var CONVERSATIONS_PER_LAWYER=${LOAD_CONVERSATIONS_PER_LAWYER}; var MESSAGES_PER_CONVERSATION=${LOAD_MESSAGES_PER_CONVERSATION}; var UUID_MODE='${LOAD_UUID_MODE}'; var LAWYER_IDS=[${LAWYER_IDS}]; var ORG_IDS=[${ORG_IDS}];" \
  --file /seed/conversations.js

log "Логины: load-lawyer-1..${LOAD_LAWYERS}@load.pravoos.test / ${LOAD_USER_PASSWORD}"

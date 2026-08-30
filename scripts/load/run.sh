#!/usr/bin/env bash
# Прогон одного сценария: съём серверных метрик, чистка лимитера логинов, k6, сводка.
#   scripts/load/run.sh journey|search|chat-stream [доп. аргументы k6]

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

SCENARIO="${1:?сценарий: journey | search | chat-stream}"
shift || true

if [[ ! -f "${LOAD_DIR}/scenarios/${SCENARIO}.js" ]]; then
  echo "Нет сценария ${SCENARIO}.js" >&2
  exit 1
fi

require_stack_up api-gateway ai-service user-service llm-service redis

RUN_ID="$(date -u +%Y%m%d-%H%M%S)-${SCENARIO}"
RUN_DIR="${LOAD_DIR}/report/${RUN_ID}"
mkdir -p "${RUN_DIR}"

cp "${LOAD_ENV_FILE}" "${RUN_DIR}/env.load"

# AuthController.LOGIN_MAX_PER_IP = 30 за 5 минут — константа в коде, а весь k6 приходит
# с одного адреса. Без чистки ключа профиль упирается в лимитер входа на первой минуте.
flush_login_limits() {
  while true; do
    compose exec -T redis redis-cli ${REDIS_PASSWORD:+-a "${REDIS_PASSWORD}"} --no-auth-warning \
      EVAL "for _,k in ipairs(redis.call('keys','ip_rate:login:*')) do redis.call('del',k) end return 1" 0 \
      >/dev/null 2>&1 || true
    sleep 2
  done
}

"${LOAD_DIR}/snapshot.sh" sample "${RUN_DIR}" "${LOAD_SNAPSHOT_INTERVAL:-5}" &
SNAPSHOT_PID=$!
flush_login_limits &
FLUSH_PID=$!

cleanup() {
  kill "${SNAPSHOT_PID}" "${FLUSH_PID}" 2>/dev/null || true
  wait "${SNAPSHOT_PID}" "${FLUSH_PID}" 2>/dev/null || true
}
trap cleanup EXIT

log "Прогон ${SCENARIO}, отчёт: ${RUN_DIR}"
set +e
compose --profile load run --rm k6 run \
  --summary-export "/report/${RUN_ID}/k6-summary.json" \
  --out "csv=/report/${RUN_ID}/k6-metrics.csv" \
  "/scenarios/${SCENARIO}.js" "$@" | tee "${RUN_DIR}/k6-console.txt"
K6_EXIT=${PIPESTATUS[0]}
set -e

cleanup
trap - EXIT

curl -sf --max-time 5 http://localhost:8085/stats > "${RUN_DIR}/mock-openai-stats.json" 2>/dev/null || true
"${LOAD_DIR}/snapshot.sh" summary "${RUN_DIR}"

log "k6 завершился с кодом ${K6_EXIT} (ненулевой = не выдержан порог)"
log "Отчёт: ${RUN_DIR}"
exit "${K6_EXIT}"

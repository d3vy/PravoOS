#!/usr/bin/env bash
# Съём серверных метрик во время прогона.
#   snapshot.sh sample <каталог> [интервал_сек]  — цикл до SIGTERM, пишет metrics.tsv
#   snapshot.sh summary <каталог>                — сводит metrics.tsv в metrics.md
#
# k6 меряет клиентскую сторону. Пулы, очереди и серверные квантили видны только здесь.
# Формат TSV, а не CSV: в метках Prometheus запятые встречаются, табуляции — нет.

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TARGETS=(
  "api-gateway=http://localhost:8090/actuator/prometheus"
  "user-service=http://localhost:8081/actuator/prometheus"
  "ai-service=http://localhost:8082/actuator/prometheus"
  "llm-service=http://localhost:8084/actuator/prometheus"
)

INTERESTING='^(hikaricp_connections|tomcat_threads|executor_|jvm_threads_live_threads|process_cpu_usage|system_cpu_usage|http_server_requests_seconds|pravoos_agent_|pravoos_login_|pravoos_guard_|llm_)'

scrape() {
  local service="$1" url="$2" stamp="$3"
  curl -sf --max-time 5 "${url}" 2>/dev/null | awk \
    -v service="${service}" -v stamp="${stamp}" -v interesting="${INTERESTING}" '
    /^#/ { next }
    $0 ~ interesting {
      value = $NF
      series = $0
      sub(/[ \t][^ \t]*$/, "", series)
      gsub(/"/, "", series)
      printf "%s\t%s\t%s\t%s\n", stamp, service, series, value
    }'
}

sample_loop() {
  local out_dir="$1" interval="${2:-5}"
  mkdir -p "${out_dir}"
  local tsv="${out_dir}/metrics.tsv"
  printf 'timestamp\tservice\tseries\tvalue\n' > "${tsv}"
  trap 'exit 0' TERM INT
  while true; do
    local stamp
    stamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    for target in "${TARGETS[@]}"; do
      scrape "${target%%=*}" "${target#*=}" "${stamp}" >> "${tsv}" || true
    done
    sleep "${interval}"
  done
}

table() {
  local tsv="$1" filter="$2" mode="$3"
  awk -F'\t' -v filter="${filter}" -v mode="${mode}" '
    NR > 1 && $3 ~ filter {
      key = $2 "\t" $3
      value = $4 + 0
      if (!(key in peak) || value > peak[key]) peak[key] = value
      last[key] = value
    }
    END {
      for (key in last) {
        if (mode == "peak") {
          printf "| %s | `%s` | %g | %g |\n", substr(key, 1, index(key, "\t") - 1),
                 substr(key, index(key, "\t") + 1), peak[key], last[key]
        } else {
          printf "| %s | `%s` | %g |\n", substr(key, 1, index(key, "\t") - 1),
                 substr(key, index(key, "\t") + 1), last[key]
        }
      }
    }' "${tsv}" | sort
}

summarize() {
  local out_dir="$1"
  local tsv="${out_dir}/metrics.tsv"
  local md="${out_dir}/metrics.md"
  if [[ ! -s "${tsv}" ]]; then
    echo "Нет ${tsv}" >&2
    exit 1
  fi

  {
    echo "# Серверные метрики прогона"
    echo
    echo "Источник: \`$(basename "${tsv}")\`, снято $(( $(wc -l < "${tsv}") - 1 )) строк."
    echo
    echo "## Пулы соединений и потоки"
    echo
    echo "| Сервис | Серия | max за прогон | последнее |"
    echo "|---|---|---|---|"
    table "${tsv}" '^(hikaricp_connections|tomcat_threads|executor_(active|queued|pool_size|completed))' peak
    echo
    echo "## Квантили http_server_requests, сек (последний срез)"
    echo
    echo "| Сервис | Серия | значение |"
    echo "|---|---|---|"
    table "${tsv}" '^http_server_requests_seconds\{.*quantile=' last
    echo
    echo "## Агент, вход, LLM (последний срез)"
    echo
    echo "| Сервис | Серия | значение |"
    echo "|---|---|---|"
    table "${tsv}" '^(pravoos_|llm_)' last
  } > "${md}"

  log "Сводка: ${md}"
}

case "${1:-}" in
  sample) sample_loop "${2:?каталог отчёта}" "${3:-5}" ;;
  summary) summarize "${2:?каталог отчёта}" ;;
  *)
    echo "Использование: snapshot.sh sample <каталог> [интервал] | snapshot.sh summary <каталог>" >&2
    exit 1
    ;;
esac

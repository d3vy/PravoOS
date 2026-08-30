#!/usr/bin/env bash
# Управление нагрузочным стендом. Поднимаются только сервисы, участвующие в профиле:
# Grafana, Tempo, Loki, Alloy и Alertmanager к прогону отношения не имеют и не нужны.

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

STACK_SERVICES=(
  postgres mongodb redis kafka clamav
  discovery-server config-server
  mock-openai llm-service user-service ai-service api-gateway
)

case "${1:-up}" in
  up)
    log "Сборка и запуск стенда"
    compose build "${STACK_SERVICES[@]}"
    compose up -d "${STACK_SERVICES[@]}"
    log "Жду готовности api-gateway"
    for _ in $(seq 1 60); do
      if curl -sf --max-time 3 http://localhost:8090/actuator/health/readiness >/dev/null 2>&1; then
        log "Стенд готов"
        exit 0
      fi
      sleep 10
    done
    echo "api-gateway не поднялся за 10 минут" >&2
    compose ps
    exit 1
    ;;
  down)
    compose down
    ;;
  status)
    compose ps "${STACK_SERVICES[@]}"
    ;;
  logs)
    shift
    compose logs -f "$@"
    ;;
  *)
    echo "Использование: stack.sh [up|down|status|logs <сервис>]" >&2
    exit 1
    ;;
esac

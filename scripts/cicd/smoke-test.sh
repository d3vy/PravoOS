#!/usr/bin/env bash
# Post-deploy smoke test: waits until every application container reports a
# healthy Docker state, then (optionally) checks the public URL responds.
#
# Env:
#   COMPOSE_FILES   docker compose -f arguments (default: base + prod)
#   SMOKE_TIMEOUT   seconds to wait for health (default 180)
#   SMOKE_INTERVAL  poll interval seconds (default 6)
#   SMOKE_URL       optional public URL to curl for a 2xx/3xx after health is up

set -euo pipefail
# shellcheck disable=SC2034  # read by lib/log.sh
SCRIPT_TAG=smoke
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/log.sh"
source "${SCRIPT_DIR}/lib/registry.sh"
source "${SCRIPT_DIR}/lib/health.sh"

COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
SMOKE_TIMEOUT="${SMOKE_TIMEOUT:-180}"
SMOKE_INTERVAL="${SMOKE_INTERVAL:-6}"

container_health() {
  local name="$1" cid
  cid="$(docker ps -aqf "name=^${name}$" 2>/dev/null || true)"
  [[ -n "$cid" ]] || { echo ""; return 0; }
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo ""
}

collect_statuses() {
  local service
  for service in "${PRAVOOS_HEALTHCHECKED_SERVICES[@]}"; do
    echo "${service}=$(container_health "$(container_name "$service")")"
  done
}

wait_for_health() {
  local deadline=$(( SECONDS + SMOKE_TIMEOUT )) statuses verdict
  while :; do
    statuses="$(collect_statuses)"
    verdict="$(printf '%s\n' "$statuses" | health_aggregate)"
    case "$verdict" in
      ok)
        log "all services healthy"
        return 0
        ;;
      fail)
        err "a service is unhealthy:"
        printf '%s\n' "$statuses" | sed 's/^/    /' >&2
        return 1
        ;;
    esac
    if (( SECONDS >= deadline )); then
      err "timed out after ${SMOKE_TIMEOUT}s waiting for health:"
      printf '%s\n' "$statuses" | sed 's/^/    /' >&2
      return 1
    fi
    log "waiting for services to become healthy..."
    sleep "$SMOKE_INTERVAL"
  done
}

check_public_url() {
  [[ -n "${SMOKE_URL:-}" ]] || return 0
  log "probing ${SMOKE_URL}"
  local code
  code="$(curl -fsS -o /dev/null -w '%{http_code}' --max-time 20 "$SMOKE_URL" 2>/dev/null || echo 000)"
  [[ "$code" =~ ^[23] ]] || die "public URL ${SMOKE_URL} returned HTTP ${code}"
  log "public URL responded with HTTP ${code}"
}

main() {
  require_command docker
  wait_for_health
  check_public_url
  log "smoke test passed"
}

main "$@"

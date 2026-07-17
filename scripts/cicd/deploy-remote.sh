#!/usr/bin/env bash
# Runs ON the deploy host (staging or prod). Pulls the requested image tag,
# rolls the stack forward, smoke-tests it, and auto-reverts to the previous
# known-good tag if the smoke test fails.
#
# Env (required):
#   IMAGE_TAG          image tag to deploy (git SHA)
# Env (optional):
#   IMAGE_REGISTRY     default ghcr.io/d3vy/pravoos
#   COMPOSE_FILES      default: base + prod + images overlay
#   DEPLOY_STATE_FILE  default: .deploy/state.env
#   SMOKE_URL          public URL to probe after health
#   REGISTRY_USERNAME / REGISTRY_TOKEN  registry login (private images)

set -euo pipefail
# shellcheck disable=SC2034  # read by lib/log.sh
SCRIPT_TAG=deploy
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
source "${SCRIPT_DIR}/lib/log.sh"
source "${SCRIPT_DIR}/lib/registry.sh"
source "${SCRIPT_DIR}/lib/deploy_state.sh"

cd "$ROOT"

IMAGE_REGISTRY="$(image_registry)"
export IMAGE_REGISTRY
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.images.yml}"
DEPLOY_STATE_FILE="${DEPLOY_STATE_FILE:-.deploy/state.env}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-240}"

# COMPOSE_FILES is a deliberately word-split list of `-f <file>` arguments.
# shellcheck disable=SC2086
compose() { docker compose ${COMPOSE_FILES} "$@"; }

registry_login() {
  [[ -n "${REGISTRY_TOKEN:-}" ]] || return 0
  log "logging in to ${IMAGE_REGISTRY%%/*}"
  echo "$REGISTRY_TOKEN" | docker login "${IMAGE_REGISTRY%%/*}" \
    -u "${REGISTRY_USERNAME:-x}" --password-stdin
}

roll_to() {
  local tag="$1"
  export IMAGE_TAG="$tag"
  log "pulling images @ ${tag}"
  compose pull
  log "starting stack @ ${tag}"
  compose up -d --remove-orphans --wait --wait-timeout "$WAIT_TIMEOUT"
}

smoke() {
  COMPOSE_FILES="$COMPOSE_FILES" SMOKE_URL="${SMOKE_URL:-}" \
    "${SCRIPT_DIR}/smoke-test.sh"
}

rollback() {
  local previous
  if ! previous="$(deploy_state_rollback "$DEPLOY_STATE_FILE")"; then
    err "no previous tag recorded — cannot roll back automatically"
    return 1
  fi
  warn "rolling back to previous good tag: ${previous}"
  roll_to "$previous"
  if smoke; then
    warn "rollback to ${previous} succeeded; investigate the failed release ${IMAGE_TAG_TARGET}"
  else
    err "rollback to ${previous} ALSO failed — manual intervention required"
  fi
}

main() {
  require_command docker
  require_env IMAGE_TAG
  IMAGE_TAG_TARGET="$IMAGE_TAG"

  registry_login
  deploy_state_promote "$DEPLOY_STATE_FILE" "$IMAGE_TAG_TARGET"

  # In an `if` condition set -e is suspended, so a failed pull / unhealthy
  # `up --wait` / failed smoke all fall through to the rollback path.
  if roll_to "$IMAGE_TAG_TARGET" && smoke; then
    log "release ${IMAGE_TAG_TARGET} is live and healthy"
    return 0
  fi

  err "release ${IMAGE_TAG_TARGET} failed to come up healthy"
  rollback
  exit 1
}

main "$@"

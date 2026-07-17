#!/usr/bin/env bash
# Manual rollback: redeploy the previous known-good tag recorded in the deploy
# state file. Runs ON the deploy host. Used by the rollback workflow and as a
# break-glass command over SSH.

set -euo pipefail
# shellcheck disable=SC2034  # read by lib/log.sh
SCRIPT_TAG=rollback
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/log.sh"
source "${SCRIPT_DIR}/lib/deploy_state.sh"

DEPLOY_STATE_FILE="${DEPLOY_STATE_FILE:-.deploy/state.env}"

main() {
  local target="${1:-}"
  if [[ -z "$target" ]]; then
    target="$(deploy_state_previous "$DEPLOY_STATE_FILE")"
    [[ -n "$target" ]] || die "no previous tag recorded and none given as argument"
  fi
  log "redeploying tag: ${target}"
  IMAGE_TAG="$target" "${SCRIPT_DIR}/deploy-remote.sh"
}

main "$@"

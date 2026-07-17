#!/usr/bin/env bash
# Persists which image tag is live so a failed release can roll back to the
# previous known-good one. State is a tiny key=value file on the deploy host.

set -euo pipefail

deploy_state_current() {
  local file="$1"
  [[ -f "$file" ]] || { echo ""; return 0; }
  # shellcheck disable=SC2002
  grep -E '^CURRENT_TAG=' "$file" | tail -n1 | cut -d= -f2- || true
}

deploy_state_previous() {
  local file="$1"
  [[ -f "$file" ]] || { echo ""; return 0; }
  grep -E '^PREVIOUS_TAG=' "$file" | tail -n1 | cut -d= -f2- || true
}

_deploy_state_write() {
  local file="$1" current="$2" previous="$3"
  mkdir -p "$(dirname "$file")"
  printf 'CURRENT_TAG=%s\nPREVIOUS_TAG=%s\n' "$current" "$previous" >"$file"
}

# Promote a new tag: the outgoing CURRENT becomes PREVIOUS (unless the tag is
# unchanged, so a redeploy of the same tag never poisons the rollback target).
deploy_state_promote() {
  local file="$1" new_tag="$2"
  local current previous
  current="$(deploy_state_current "$file")"
  previous="$(deploy_state_previous "$file")"
  if [[ "$new_tag" == "$current" ]]; then
    _deploy_state_write "$file" "$new_tag" "$previous"
  else
    _deploy_state_write "$file" "$new_tag" "$current"
  fi
}

# Roll back: PREVIOUS becomes CURRENT again. Prints the tag to restore, or fails
# with a non-zero status when there is nothing to roll back to.
deploy_state_rollback() {
  local file="$1"
  local current previous
  current="$(deploy_state_current "$file")"
  previous="$(deploy_state_previous "$file")"
  [[ -n "$previous" ]] || return 1
  _deploy_state_write "$file" "$previous" "$current"
  echo "$previous"
}

#!/usr/bin/env bash
# Unit tests for lib/deploy_state.sh promote/rollback bookkeeping.
set -euo pipefail

test_deploy_state() {
  echo "deploy_state.sh"
  local dir file
  dir="$(mktemp -d)"
  file="${dir}/state.env"
  trap 'rm -rf "$dir"' RETURN

  assert_eq "" "$(deploy_state_current "$file")" "empty state -> no current"
  assert_eq "" "$(deploy_state_previous "$file")" "empty state -> no previous"

  deploy_state_promote "$file" sha-1
  assert_eq "sha-1" "$(deploy_state_current "$file")" "first promote sets current"
  assert_eq "" "$(deploy_state_previous "$file")" "first promote has no previous"

  deploy_state_promote "$file" sha-2
  assert_eq "sha-2" "$(deploy_state_current "$file")" "second promote advances current"
  assert_eq "sha-1" "$(deploy_state_previous "$file")" "outgoing becomes previous"

  deploy_state_promote "$file" sha-2
  assert_eq "sha-1" "$(deploy_state_previous "$file")" \
    "re-promoting same tag keeps previous (no self-poison)"

  local restored
  restored="$(deploy_state_rollback "$file")"
  assert_eq "sha-1" "$restored" "rollback returns previous tag"
  assert_eq "sha-1" "$(deploy_state_current "$file")" "rollback restores current"
  assert_eq "sha-2" "$(deploy_state_previous "$file")" "rollback keeps the reverted tag as previous"

  local empty_file="${dir}/empty.env"
  if deploy_state_rollback "$empty_file" >/dev/null 2>&1; then
    assert_eq "fail" "ok" "rollback with no previous should error"
  else
    assert_eq "ok" "ok" "rollback with no previous errors"
  fi
}

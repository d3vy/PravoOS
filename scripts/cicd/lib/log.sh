#!/usr/bin/env bash
# Minimal structured logging + guard helpers shared by every cicd script.

set -euo pipefail

_log_prefix() {
  echo "[$(date -u '+%H:%M:%S')] ${SCRIPT_TAG:-cicd}"
}

log()  { echo "$(_log_prefix) $*"; }
warn() { echo "$(_log_prefix) WARN  $*" >&2; }
err()  { echo "$(_log_prefix) ERROR $*" >&2; }

die() {
  err "$*"
  exit 1
}

require_command() {
  local cmd
  for cmd in "$@"; do
    command -v "$cmd" >/dev/null 2>&1 || die "required command not found: $cmd"
  done
}

require_env() {
  local name
  for name in "$@"; do
    [[ -n "${!name:-}" ]] || die "required environment variable not set: $name"
  done
}

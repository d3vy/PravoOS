#!/usr/bin/env bash
# Pure health-status classification, isolated from Docker so it can be unit-tested.

set -euo pipefail

# Maps a Docker container health/running status to one of: ok | fail | wait.
#   healthy / running (no healthcheck) -> ok
#   unhealthy / exited / dead          -> fail
#   starting / created / empty         -> wait
health_classify() {
  case "${1:-}" in
    healthy|running) echo ok ;;
    unhealthy|exited|dead|restarting) echo fail ;;
    starting|created|"") echo wait ;;
    *) echo wait ;;
  esac
}

# Given newline-separated "service=status" lines, prints the aggregate verdict:
#   ok   — every service is ok
#   fail — at least one service failed
#   wait — none failed but some are still starting
health_aggregate() {
  local line status verdict=ok
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    status="$(health_classify "${line#*=}")"
    case "$status" in
      fail) echo fail; return 0 ;;
      wait) verdict="wait" ;;
    esac
  done
  echo "$verdict"
}

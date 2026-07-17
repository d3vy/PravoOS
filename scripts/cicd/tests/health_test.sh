#!/usr/bin/env bash
# Unit tests for lib/health.sh classification and aggregation.
set -euo pipefail

test_health() {
  echo "health.sh"
  assert_eq ok   "$(health_classify healthy)"    "healthy -> ok"
  assert_eq ok   "$(health_classify running)"    "running (no healthcheck) -> ok"
  assert_eq fail "$(health_classify unhealthy)"  "unhealthy -> fail"
  assert_eq fail "$(health_classify exited)"     "exited -> fail"
  assert_eq wait "$(health_classify starting)"   "starting -> wait"
  assert_eq wait "$(health_classify '')"         "missing -> wait"

  assert_eq ok "$(printf 'a=healthy\nb=running\n' | health_aggregate)" \
    "all healthy -> ok"
  assert_eq fail "$(printf 'a=healthy\nb=unhealthy\n' | health_aggregate)" \
    "any unhealthy -> fail"
  assert_eq wait "$(printf 'a=healthy\nb=starting\n' | health_aggregate)" \
    "some starting -> wait"
  assert_eq fail "$(printf 'a=starting\nb=unhealthy\n' | health_aggregate)" \
    "fail dominates wait"
}

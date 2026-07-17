#!/usr/bin/env bash
# Tiny zero-dependency assertion helper for the cicd bash unit tests.

set -euo pipefail

ASSERT_PASS=0
ASSERT_FAIL=0

assert_eq() {
  local expected="$1" actual="$2" msg="${3:-}"
  if [[ "$expected" == "$actual" ]]; then
    ASSERT_PASS=$(( ASSERT_PASS + 1 ))
    echo "  ok  ${msg}"
  else
    ASSERT_FAIL=$(( ASSERT_FAIL + 1 ))
    echo "  FAIL ${msg}"
    echo "       expected: [${expected}]"
    echo "       actual:   [${actual}]"
  fi
}

assert_summary() {
  echo ""
  echo "passed=${ASSERT_PASS} failed=${ASSERT_FAIL}"
  [[ "$ASSERT_FAIL" -eq 0 ]]
}

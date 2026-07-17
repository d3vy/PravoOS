#!/usr/bin/env bash
# Runs every cicd bash unit test. Wired into CI (see .github/workflows/_verify.yml).
set -euo pipefail

TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$(cd "${TESTS_DIR}/../lib" && pwd)"
CICD_ROOT="$(cd "${TESTS_DIR}/../../.." && pwd)"
export CICD_ROOT

source "${TESTS_DIR}/assert.sh"
source "${LIB_DIR}/health.sh"
source "${LIB_DIR}/deploy_state.sh"
source "${LIB_DIR}/registry.sh"

source "${TESTS_DIR}/health_test.sh"
source "${TESTS_DIR}/deploy_state_test.sh"
source "${TESTS_DIR}/consistency.sh"

test_health
test_deploy_state
test_consistency

assert_summary

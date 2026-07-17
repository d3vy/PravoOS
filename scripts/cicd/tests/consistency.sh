#!/usr/bin/env bash
# Guards the single-source-of-truth invariant: the deployable service list must
# be identical across registry.sh, docker-bake.hcl and docker-compose.images.yml.
set -euo pipefail

_sorted() { printf '%s\n' "$@" | sort | tr '\n' ' '; }

test_consistency() {
  echo "cross-file service list consistency"
  local root="${CICD_ROOT}"

  local from_registry
  from_registry="$(_sorted "${PRAVOOS_APP_SERVICES[@]}")"

  # Public (non-underscore) targets declared in the bake file.
  local from_bake
  from_bake="$(grep -E '^target "[a-z]' "${root}/docker-bake.hcl" \
    | sed -E 's/^target "([^"]+)".*/\1/' | sort | tr '\n' ' ')"

  # Top-level service keys in the images overlay.
  local from_compose
  from_compose="$(grep -E '^  [a-z][a-z-]+:' "${root}/docker-compose.images.yml" \
    | sed -E 's/^  ([a-z-]+):.*/\1/' | sort | tr '\n' ' ')"

  assert_eq "$from_registry" "$from_bake"    "registry.sh matches docker-bake.hcl"
  assert_eq "$from_registry" "$from_compose" "registry.sh matches docker-compose.images.yml"
}

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

# docker/Dockerfile builds the whole Maven reactor inside the image, so every
# <module> of the root pom must be COPY'd in. A module added to pom.xml but not
# to the Dockerfile fails the build with "Child module does not exist" — every
# image stops rebuilding and the server keeps running the previous jars.
test_dockerfile_covers_reactor() {
  echo "docker/Dockerfile covers every reactor module"
  local root="${CICD_ROOT}"

  local from_pom
  from_pom="$(grep -oE '<module>[^<]+</module>' "${root}/pom.xml" \
    | sed -E 's/<\/?module>//g' | sort | tr '\n' ' ')"

  local from_dockerfile
  from_dockerfile="$(grep -oE '^COPY [a-z-]+/pom\.xml' "${root}/docker/Dockerfile" \
    | sed -E 's|^COPY ||; s|/pom\.xml$||' | sort -u | tr '\n' ' ')"

  assert_eq "$from_pom" "$from_dockerfile" "pom.xml modules match Dockerfile COPY lines"
}

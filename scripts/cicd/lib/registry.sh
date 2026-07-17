#!/usr/bin/env bash
# Single source of truth for the deployable application images.
#
# The same service list is declared, by necessity, in three places:
#   - docker-bake.hcl            (how the images are built)
#   - docker-compose.images.yml  (how the images are pulled at deploy time)
#   - here                       (how deploy/smoke scripts iterate them)
# scripts/cicd/tests/consistency.sh asserts the three stay in sync.

set -euo pipefail

# shellcheck disable=SC2034  # consumed by sourcing scripts
readonly PRAVOOS_APP_SERVICES=(
  user-service
  llm-service
  ai-service
  notification-service
  api-gateway
  frontend
)

# Services with a Docker healthcheck we gate the smoke test on.
# shellcheck disable=SC2034  # consumed by sourcing scripts
readonly PRAVOOS_HEALTHCHECKED_SERVICES=(
  user-service
  llm-service
  ai-service
  notification-service
  api-gateway
  frontend
)

image_registry() {
  echo "${IMAGE_REGISTRY:-ghcr.io/d3vy/pravoos}"
}

image_ref() {
  local service="$1" tag="${2:-${IMAGE_TAG:-latest}}"
  echo "$(image_registry)/${service}:${tag}"
}

container_name() {
  echo "pravoos-$1"
}

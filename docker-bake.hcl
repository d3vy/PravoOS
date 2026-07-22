# Builds every deployable image. All five backend targets share the single
# Maven compile stage in docker/Dockerfile, so `docker buildx bake` compiles the
# reactor exactly once instead of once per service.
#
# Usage:
#   docker buildx bake                                  # build all, local
#   REGISTRY=... TAG=... SHA=... docker buildx bake --push
#
# Keep the target list in sync with scripts/cicd/lib/registry.sh and
# docker-compose.images.yml (guarded by scripts/cicd/tests/consistency.sh).

variable "REGISTRY" {
  default = "ghcr.io/d3vy/pravoos"
}

variable "TAG" {
  default = "latest"
}

# Immutable git SHA tag, added alongside TAG when provided.
variable "SHA" {
  default = ""
}

function "tags" {
  params = [name]
  result = SHA == "" ? [
    "${REGISTRY}/${name}:${TAG}",
    ] : [
    "${REGISTRY}/${name}:${TAG}",
    "${REGISTRY}/${name}:${SHA}",
  ]
}

group "default" {
  targets = [
    "discovery-server",
    "config-server",
    "user-service",
    "llm-service",
    "ai-service",
    "notification-service",
    "api-gateway",
    "frontend",
  ]
}

target "_backend" {
  context    = "."
  dockerfile = "docker/Dockerfile"
  platforms  = ["linux/amd64"]
  cache-from = ["type=gha,scope=backend"]
  cache-to   = ["type=gha,scope=backend,mode=max"]
}

target "discovery-server" {
  inherits = ["_backend"]
  target   = "discovery-server"
  tags     = tags("discovery-server")
}

target "config-server" {
  inherits = ["_backend"]
  target   = "config-server"
  tags     = tags("config-server")
}

target "user-service" {
  inherits = ["_backend"]
  target   = "user-service"
  tags     = tags("user-service")
}

target "llm-service" {
  inherits = ["_backend"]
  target   = "llm-service"
  tags     = tags("llm-service")
}

target "ai-service" {
  inherits = ["_backend"]
  target   = "ai-service"
  tags     = tags("ai-service")
}

target "notification-service" {
  inherits = ["_backend"]
  target   = "notification-service"
  tags     = tags("notification-service")
}

target "api-gateway" {
  inherits = ["_backend"]
  target   = "api-gateway"
  tags     = tags("api-gateway")
}

target "frontend" {
  context    = "./frontend"
  dockerfile = "Dockerfile"
  platforms  = ["linux/amd64"]
  tags       = tags("frontend")
  cache-from = ["type=gha,scope=frontend"]
  cache-to   = ["type=gha,scope=frontend,mode=max"]
  args = {
    VITE_SENTRY_ENVIRONMENT = "production"
  }
}

#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.prod.yml)

log() {
  echo "[deploy] $*"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command not found: $1"
    exit 1
  fi
}

load_env() {
  if [[ ! -f .env ]]; then
    echo "Missing .env — run: cp .env.example .env && nano .env"
    exit 1
  fi
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
}

validate_env() {
  local missing=()
  local key
  for key in SERVER_DOMAIN SERVER_IP ACME_EMAIL JWT_SECRET DB_PASSWORD \
    MONGO_PASSWORD ADMIN_EMAIL ADMIN_PASSWORD \
    DEEPSEEK_API_KEY TELEGRAM_BOT_TOKEN TELEGRAM_ADMIN_CHAT_ID; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("$key")
    fi
  done
  if ((${#missing[@]} > 0)); then
    echo "Fill required variables in .env:"
    printf '  - %s\n' "${missing[@]}"
    exit 1
  fi
  if [[ "$JWT_SECRET" == *changeme* ]]; then
    echo "JWT_SECRET must be a strong random value (not the example placeholder)."
    exit 1
  fi
}

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

build_sequentially() {
  log "Building Java services (one Maven run)..."
  compose build user-service
  log "Building remaining images..."
  compose build ai-service api-gateway notification-service frontend
}

wait_for_kafka() {
  log "Waiting for Kafka to become healthy (up to 3 min)..."
  for _ in {1..18}; do
    local status
    status=$(compose ps kafka --format '{{.Health}}' 2>/dev/null || true)
    if [[ "$status" == "healthy" ]]; then
      log "Kafka is healthy."
      return 0
    fi
    sleep 10
  done
  log "Kafka is not healthy yet — check: compose logs kafka --tail 50"
  return 1
}

start_infrastructure() {
  log "Starting infrastructure..."
  compose up -d postgres mongodb zookeeper
  compose up -d kafka --force-recreate
  wait_for_kafka
}

ssl_certificate_exists() {
  compose run --rm --entrypoint sh certbot \
    -c "test -f /etc/letsencrypt/live/${SERVER_DOMAIN}/fullchain.pem"
}

obtain_ssl_certificate() {
  log "Requesting Let's Encrypt certificate for ${SERVER_DOMAIN}..."
  compose run --rm --entrypoint certbot certbot certonly \
    --webroot \
    -w /var/www/certbot \
    -d "${SERVER_DOMAIN}" \
    -d "www.${SERVER_DOMAIN}" \
    --email "${ACME_EMAIL}" \
    --agree-tos \
    --no-eff-email \
    --non-interactive
}

main() {
  require_command docker
  require_command envsubst
  load_env
  validate_env

  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose v2 plugin required (docker compose)."
    exit 1
  fi

  log "Building images..."
  mkdir -p logs/{user-service,ai-service,api-gateway,notification-service}
  chmod -R a+rwx logs 2>/dev/null || true
  build_sequentially

  log "Starting stack (HTTP, certificate bootstrap)..."
  ./scripts/render-nginx.sh init
  start_infrastructure
  compose up -d

  log "Waiting for services to start (90s)..."
  sleep 90

  if ! ssl_certificate_exists; then
    obtain_ssl_certificate
  else
    log "SSL certificate already present."
  fi

  log "Enabling HTTPS..."
  ./scripts/render-nginx.sh prod
  compose up -d frontend

  log "Deployment complete."
  echo ""
  echo "  App:  https://${SERVER_DOMAIN}"
  echo "  HTTP: http://${SERVER_IP} (by IP, without TLS)"
  echo ""
  echo "Check status: docker compose -f docker-compose.yml -f docker-compose.prod.yml ps"
  echo "View logs:    docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f"
}

main "$@"

#!/usr/bin/env bash
set -euo pipefail

# Re-attach to tmux so SSH disconnect doesn't kill the deploy
if [ -z "${TMUX:-}" ] && [ -z "${STY:-}" ] && command -v tmux >/dev/null 2>&1; then
    SESSION="pravoos-deploy"
    if tmux has-session -t "$SESSION" 2>/dev/null; then
        echo "Deploy already running. Attach with: tmux attach -t $SESSION"
        exit 0
    fi
    exec tmux new-session -s "$SESSION" "$0" "$@"
fi

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
  for key in SERVER_DOMAIN SERVER_IP ACME_EMAIL JWT_SECRET DB_USERNAME DB_PASSWORD \
    MONGO_USERNAME MONGO_PASSWORD REDIS_PASSWORD ADMIN_EMAIL ADMIN_PASSWORD \
    OPENAI_API_KEY RESEND_API_KEY \
    TELEGRAM_BOT_TOKEN TELEGRAM_ADMIN_CHAT_IDS \
    TELEGRAM_ALERTS_CHAT_ID_1 TELEGRAM_ALERTS_CHAT_ID_2 \
    GRAFANA_ADMIN_USER GRAFANA_ADMIN_PASSWORD; do
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
  if [[ "$GRAFANA_ADMIN_PASSWORD" == *changeme* ]]; then
    echo "GRAFANA_ADMIN_PASSWORD must be changed from the example placeholder."
    exit 1
  fi
}

compose() {
  docker compose "${COMPOSE_FILES[@]}" "$@"
}

build_images() {
  # No explicit service list: every Dockerfile stage runs its own `mvn package`
  # from source (see docker/Dockerfile's `build` stage), so plain `compose build`
  # rebuilds every buildable service — services without a `build:` section
  # (postgres, redis, kafka, ...) are skipped automatically by Compose. This is
  # deliberate: a hardcoded per-service list here is exactly what silently left
  # llm-service on a month-old image (see git history) — never reintroduce one.
  log "Building Docker images..."
  compose build
}

start_stack() {
  log "Starting full stack — Compose enforces health-ordering via depends_on..."
  compose up -d --remove-orphans
  log "Stack started. Status:"
  compose ps
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

  log "Building application..."
  mkdir -p logs/{discovery-server,config-server,user-service,llm-service,ai-service,notification-service,api-gateway}
  chmod -R a+rwx logs 2>/dev/null || true
  build_images

  log "Starting stack (HTTP, certificate bootstrap)..."
  ./scripts/render-nginx.sh init
  start_stack

  if ! ssl_certificate_exists; then
    obtain_ssl_certificate
  else
    log "SSL certificate already present."
  fi

  if ! ssl_certificate_exists; then
    echo "SSL certificate is still missing for ${SERVER_DOMAIN} — keeping HTTP-only mode."
    echo "Fix DNS / Let's Encrypt access and re-run deploy. Site will stay on port 80 (Cloudflare 521 over HTTPS)."
    exit 1
  fi

  log "Enabling HTTPS..."
  ./scripts/render-nginx.sh prod
  compose up -d --force-recreate --wait --wait-timeout 120 frontend

  log "Deployment complete."
  echo ""
  echo "  App:  https://${SERVER_DOMAIN}"
  echo "  HTTP: http://${SERVER_IP} (by IP, without TLS)"
  echo ""
  echo "Check status: docker compose -f docker-compose.yml -f docker-compose.prod.yml ps"
  echo "View logs:    docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f"
}

main "$@"

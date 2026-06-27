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
  for key in SERVER_DOMAIN SERVER_IP ACME_EMAIL JWT_SECRET DB_PASSWORD \
    MONGO_PASSWORD ADMIN_EMAIL ADMIN_PASSWORD \
    OPENAI_API_KEY TELEGRAM_BOT_TOKEN TELEGRAM_ADMIN_CHAT_IDS; do
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

build_jars() {
  log "Building JARs (mvn package)..."
  mkdir -p ~/.m2 docker/jars
  cp docker/maven/settings.xml ~/.m2/settings.xml
  mvn package -DskipTests -B -q
  cp user-service/target/user-service-*.jar docker/jars/user-service.jar
  cp ai-service/target/ai-service-*.jar docker/jars/ai-service.jar
  cp api-gateway/target/api-gateway-*.jar docker/jars/api-gateway.jar
  cp notification-service/target/notification-service-*.jar docker/jars/notification-service.jar
}

build_images() {
  log "Building Docker images..."
  compose build user-service ai-service api-gateway notification-service frontend
}

start_stack() {
  log "Starting full stack — Compose orders by depends_on and waits for health..."
  if ! compose up -d --wait --wait-timeout "${STACK_WAIT_TIMEOUT:-600}"; then
    log "Stack did not become healthy in time."
    log "Status:"
    compose ps || true
    log "Recent logs of unhealthy services:"
    compose ps --status running --format '{{.Service}}' 2>/dev/null | while read -r svc; do
      [[ -n "$svc" ]] && log "  see: docker compose logs $svc --tail 80"
    done
    return 1
  fi
  log "All services healthy."
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
  require_command mvn
  require_command java
  load_env
  validate_env

  if ! docker compose version >/dev/null 2>&1; then
    echo "Docker Compose v2 plugin required (docker compose)."
    exit 1
  fi

  log "Building application..."
  mkdir -p logs/{user-service,ai-service,api-gateway,notification-service}
  chmod -R a+rwx logs 2>/dev/null || true
  build_jars
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

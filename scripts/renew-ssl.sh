#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILES=(-f docker-compose.yml -f docker-compose.prod.yml)

set -a
# shellcheck disable=SC1091
source .env
set +a

docker compose "${COMPOSE_FILES[@]}" run --rm --entrypoint certbot certbot renew --quiet
docker compose "${COMPOSE_FILES[@]}" exec -T frontend nginx -s reload

echo "SSL certificates renewed."

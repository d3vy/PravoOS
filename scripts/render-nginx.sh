#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODE="${1:-}"
if [[ "$MODE" != "init" && "$MODE" != "prod" ]]; then
  echo "Usage: $0 <init|prod>"
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "Missing .env — copy from .env.example and fill in values."
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

if [[ -z "${SERVER_DOMAIN:-}" || -z "${SERVER_IP:-}" ]]; then
  echo "SERVER_DOMAIN and SERVER_IP must be set in .env"
  exit 1
fi

if ! command -v envsubst >/dev/null 2>&1; then
  echo "envsubst not found. Install: apt install gettext-base"
  exit 1
fi

mkdir -p docker/nginx/generated
export SERVER_DOMAIN SERVER_IP

if [[ "$MODE" == "init" ]]; then
  envsubst '${SERVER_DOMAIN} ${SERVER_IP}' \
    < docker/nginx/app.init.conf.template \
    > docker/nginx/generated/default.conf
else
  envsubst '${SERVER_DOMAIN} ${SERVER_IP}' \
    < docker/nginx/app.prod.conf.template \
    > docker/nginx/generated/default.conf
fi

echo "Nginx config rendered ($MODE) -> docker/nginx/generated/default.conf"

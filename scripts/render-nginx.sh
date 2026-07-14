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

CF_ORIGIN_PULL="${CF_ORIGIN_PULL:-off}"
if [[ "$CF_ORIGIN_PULL" != "on" && "$CF_ORIGIN_PULL" != "off" ]]; then
  echo "CF_ORIGIN_PULL must be 'on' or 'off', got: $CF_ORIGIN_PULL"
  exit 1
fi

# Sentry/GlitchTip ingest ходит с браузера — иначе CSP connect-src 'self' его заблокирует.
CSP_CONNECT_EXTRA="${SENTRY_INGEST_ORIGIN:-}"

mkdir -p docker/nginx/generated
export SERVER_DOMAIN SERVER_IP CF_ORIGIN_PULL CSP_CONNECT_EXTRA

if [[ "$MODE" == "init" ]]; then
  envsubst '${SERVER_DOMAIN} ${SERVER_IP} ${CSP_CONNECT_EXTRA}' \
    < docker/nginx/app.init.conf.template \
    > docker/nginx/generated/default.conf
else
  envsubst '${SERVER_DOMAIN} ${SERVER_IP} ${CF_ORIGIN_PULL} ${CSP_CONNECT_EXTRA}' \
    < docker/nginx/app.prod.conf.template \
    > docker/nginx/generated/default.conf
fi

echo "Nginx config rendered ($MODE) -> docker/nginx/generated/default.conf"

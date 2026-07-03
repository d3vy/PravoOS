#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID:-}" -ne 0 ]]; then
  echo "Run as root: sudo $0"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates curl git gettext-base ufw openjdk-21-jdk-headless maven

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi

if ! docker compose version >/dev/null 2>&1; then
  apt-get install -y docker-compose-plugin
fi

systemctl enable docker
systemctl start docker

ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw --force enable

"$(dirname "$0")/lockdown-origin.sh"

echo ""
echo "Server ready. Next steps:"
echo "  1. Clone repo into /opt/pravoos (or your path)"
echo "  2. cp .env.example .env && nano .env"
echo "  3. Point DNS A record for your domain to this server's IP"
echo "  4. ./scripts/deploy.sh"

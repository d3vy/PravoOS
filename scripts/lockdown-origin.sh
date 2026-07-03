#!/usr/bin/env bash
set -euo pipefail

# Restricts inbound 80/443 to Cloudflare edge ranges only, so the origin
# cannot be reached directly (bypassing CF WAF/DDoS protection).
# Idempotent: re-running adds missing rules and removes the broad allows.
# Re-run after Cloudflare publishes new ranges (https://www.cloudflare.com/ips/).

if [[ "${EUID:-}" -ne 0 ]]; then
  echo "Run as root: sudo $0"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REALIP_CONF="$ROOT/docker/nginx/cloudflare-realip.conf"

IPV4="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v4)"
IPV6="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v6)"

CIDR_V4='^([0-9]{1,3}\.){3}[0-9]{1,3}/[0-9]{1,2}$'
CIDR_V6='^[0-9a-fA-F:]+/[0-9]{1,3}$'

RANGES=()
while IFS= read -r cidr; do
  [[ -z "$cidr" ]] && continue
  if [[ ! "$cidr" =~ $CIDR_V4 ]]; then
    echo "Unexpected line in ips-v4 response: '$cidr' — aborting, ufw untouched."
    exit 1
  fi
  RANGES+=("$cidr")
done <<< "$IPV4"

while IFS= read -r cidr; do
  [[ -z "$cidr" ]] && continue
  if [[ ! "$cidr" =~ $CIDR_V6 ]]; then
    echo "Unexpected line in ips-v6 response: '$cidr' — aborting, ufw untouched."
    exit 1
  fi
  RANGES+=("$cidr")
done <<< "$IPV6"

if [[ "${#RANGES[@]}" -lt 10 ]]; then
  echo "Only ${#RANGES[@]} ranges fetched — response looks wrong, aborting."
  exit 1
fi

for cidr in "${RANGES[@]}"; do
  ufw allow proto tcp from "$cidr" to any port 80,443 comment 'cloudflare-edge' >/dev/null
done

ufw delete allow 80/tcp >/dev/null 2>&1 || true
ufw delete allow 443/tcp >/dev/null 2>&1 || true

echo "ufw: 80/443 restricted to ${#RANGES[@]} Cloudflare ranges."

if [[ -f "$REALIP_CONF" ]]; then
  MISSING=0
  for cidr in "${RANGES[@]}"; do
    if ! grep -q "set_real_ip_from ${cidr};" "$REALIP_CONF"; then
      echo "DRIFT: $cidr is live at Cloudflare but missing in cloudflare-realip.conf"
      MISSING=1
    fi
  done
  if [[ "$MISSING" -eq 1 ]]; then
    echo "Update docker/nginx/cloudflare-realip.conf and reload nginx, or real client IPs will be lost for the new ranges."
  fi
fi

echo ""
echo "Reminder: stale 'cloudflare-edge' rules for ranges Cloudflare retired are NOT auto-removed."
echo "Review with: ufw status numbered | grep cloudflare-edge"

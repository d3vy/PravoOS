#!/usr/bin/env bash
set -euo pipefail

# Restricts inbound 80/443 to Cloudflare edge ranges only, so the origin
# cannot be reached directly (bypassing CF WAF/DDoS protection).
#
# Two layers, because Docker publishes ports through iptables FORWARD/DOCKER-USER
# and BYPASSES ufw's INPUT rules entirely — plain `ufw allow` does NOT protect
# ports published by docker-compose:
#   1. ufw INPUT rules (host-level services, defense in depth)
#   2. DOCKER-USER rules via /etc/ufw/after.rules (the layer that actually
#      filters traffic to the frontend container; persists across reboot/reload)
#
# Idempotent: re-running refreshes both layers.
# Re-run after Cloudflare publishes new ranges (https://www.cloudflare.com/ips/).

if [[ "${EUID:-}" -ne 0 ]]; then
  echo "Run as root: sudo $0"
  exit 1
fi

if ! ufw status | grep -q "Status: active"; then
  echo "ufw is not active — rules would be written but never applied."
  echo "Check SSH is allowed (ufw show added | grep -w 22), then: sudo ufw enable"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REALIP_CONF="$ROOT/docker/nginx/cloudflare-realip.conf"
AFTER_RULES="/etc/ufw/after.rules"
MARK_BEGIN="# BEGIN PRAVOOS-CF-LOCKDOWN"
MARK_END="# END PRAVOOS-CF-LOCKDOWN"

IPV4="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v4)"
IPV6="$(curl -fsS --max-time 15 https://www.cloudflare.com/ips-v6)"

CIDR_V4='^([0-9]{1,3}\.){3}[0-9]{1,3}/[0-9]{1,2}$'
CIDR_V6='^[0-9a-fA-F:]+/[0-9]{1,3}$'

RANGES_V4=()
while IFS= read -r cidr; do
  [[ -z "$cidr" ]] && continue
  if [[ ! "$cidr" =~ $CIDR_V4 ]]; then
    echo "Unexpected line in ips-v4 response: '$cidr' — aborting, firewall untouched."
    exit 1
  fi
  RANGES_V4+=("$cidr")
done <<< "$IPV4"

RANGES_V6=()
while IFS= read -r cidr; do
  [[ -z "$cidr" ]] && continue
  if [[ ! "$cidr" =~ $CIDR_V6 ]]; then
    echo "Unexpected line in ips-v6 response: '$cidr' — aborting, firewall untouched."
    exit 1
  fi
  RANGES_V6+=("$cidr")
done <<< "$IPV6"

if [[ "$(( ${#RANGES_V4[@]} + ${#RANGES_V6[@]} ))" -lt 10 ]]; then
  echo "Only $(( ${#RANGES_V4[@]} + ${#RANGES_V6[@]} )) ranges fetched — response looks wrong, aborting."
  exit 1
fi

# --- Layer 1: ufw INPUT (host-level; does NOT cover docker-published ports) ---

for cidr in "${RANGES_V4[@]}" "${RANGES_V6[@]}"; do
  ufw allow proto tcp from "$cidr" to any port 80,443 comment 'cloudflare-edge' >/dev/null
done

ufw delete allow 80/tcp >/dev/null 2>&1 || true
ufw delete allow 443/tcp >/dev/null 2>&1 || true

# --- Layer 2: DOCKER-USER via after.rules (the one that actually matters) ---

BLOCK_FILE="$(mktemp)"
{
  echo "$MARK_BEGIN"
  echo "*filter"
  echo ":DOCKER-USER - [0:0]"
  echo "-A DOCKER-USER -m conntrack --ctstate ESTABLISHED,RELATED -j RETURN"
  echo "-A DOCKER-USER -s 10.0.0.0/8 -j RETURN"
  echo "-A DOCKER-USER -s 172.16.0.0/12 -j RETURN"
  echo "-A DOCKER-USER -s 192.168.0.0/16 -j RETURN"
  for cidr in "${RANGES_V4[@]}"; do
    echo "-A DOCKER-USER -p tcp -m multiport --dports 80,443 -s $cidr -j RETURN"
  done
  echo "-A DOCKER-USER -p tcp -m multiport --dports 80,443 -j DROP"
  echo "-A DOCKER-USER -j RETURN"
  echo "COMMIT"
  echo "$MARK_END"
} > "$BLOCK_FILE"

cp "$AFTER_RULES" "${AFTER_RULES}.bak"
sed -i "/^${MARK_BEGIN}\$/,/^${MARK_END}\$/d" "$AFTER_RULES"
cat "$BLOCK_FILE" >> "$AFTER_RULES"
rm -f "$BLOCK_FILE"

if ! ufw reload; then
  echo "ufw reload FAILED — restoring ${AFTER_RULES}.bak"
  cp "${AFTER_RULES}.bak" "$AFTER_RULES"
  ufw reload
  exit 1
fi

echo "ufw INPUT: 80/443 restricted to $(( ${#RANGES_V4[@]} + ${#RANGES_V6[@]} )) Cloudflare ranges."
echo "DOCKER-USER: 80/443 restricted to ${#RANGES_V4[@]} Cloudflare IPv4 ranges (docker publishes v4 only)."

# --- Drift check against nginx realip config ---

if [[ -f "$REALIP_CONF" ]]; then
  MISSING=0
  for cidr in "${RANGES_V4[@]}" "${RANGES_V6[@]}"; do
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
echo "Verify from an external network (NOT from this server — local traffic bypasses the filter):"
echo "  curl -sI --max-time 5 https://<origin-ip> -k   # must time out"
echo "Stale ufw 'cloudflare-edge' INPUT rules for retired ranges are not auto-removed:"
echo "  ufw status numbered | grep cloudflare-edge"

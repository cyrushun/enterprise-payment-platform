#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
[[ -f .env ]] || { echo "ERROR: .env is missing." >&2; exit 1; }
[[ -s .deployment/previous-image ]] || { echo "ERROR: no recorded rollback image." >&2; exit 1; }
PAYMENT_API_IMAGE="$(cat .deployment/previous-image)"
[[ "$PAYMENT_API_IMAGE" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+@sha256:[0-9a-f]{64}$ ]] || { echo "ERROR: invalid recorded rollback image." >&2; exit 1; }
export PAYMENT_API_IMAGE
COMPOSE=(docker compose --env-file .env --profile deployment)
"${COMPOSE[@]}" pull payment-api
"${COMPOSE[@]}" up -d --no-build --pull always payment-api
PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health/readiness"
for attempt in $(seq 1 36); do
  if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    printf '%s' "$PAYMENT_API_IMAGE" > .deployment/current-image
    echo "Rollback succeeded: $PAYMENT_API_IMAGE"; exit 0
  fi
  sleep 5
done
echo "ERROR: rollback image did not become ready." >&2
exit 1

#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
[[ -f .env ]] || { echo "ERROR: .env is missing." >&2; exit 1; }
[[ -s .deployment/previous-image && -s .deployment/previous-frontend-image ]] || { echo "ERROR: both API and frontend rollback images must be recorded." >&2; exit 1; }
PAYMENT_API_IMAGE="$(cat .deployment/previous-image)"
PAYMENT_FRONTEND_IMAGE="$(cat .deployment/previous-frontend-image)"
for image in "$PAYMENT_API_IMAGE" "$PAYMENT_FRONTEND_IMAGE"; do
  [[ "$image" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+@sha256:[0-9a-f]{64}$ ]] || { echo "ERROR: invalid recorded rollback image." >&2; exit 1; }
done
export PAYMENT_API_IMAGE PAYMENT_FRONTEND_IMAGE
COMPOSE=(docker compose --env-file .env --profile deployment)
"${COMPOSE[@]}" pull payment-api frontend
"${COMPOSE[@]}" up -d --no-build --pull always payment-api frontend frontend-proxy
PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health/readiness"
FRONTEND_HEALTH_URL="http://127.0.0.1:${FRONTEND_HTTP_PORT:-8088}/healthz"
for attempt in $(seq 1 36); do
  if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '\"status\"[[:space:]]*:[[:space:]]*\"UP\"' && curl --silent --show-error --fail --max-time 5 "$FRONTEND_HEALTH_URL" | grep -q ok; then
    printf '%s' "$PAYMENT_API_IMAGE" > .deployment/current-image
    printf '%s' "$PAYMENT_FRONTEND_IMAGE" > .deployment/current-frontend-image
    echo "API and frontend rollback succeeded."; exit 0
  fi
  sleep 5
done
echo "ERROR: rollback images did not both become healthy." >&2
exit 1

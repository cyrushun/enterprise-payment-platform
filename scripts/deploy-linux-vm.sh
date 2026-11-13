#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
: "${PAYMENT_API_IMAGE:?Set PAYMENT_API_IMAGE to an immutable GHCR image reference}"
: "${PAYMENT_FRONTEND_IMAGE:?Set PAYMENT_FRONTEND_IMAGE to an immutable GHCR image reference}"
for image in "$PAYMENT_API_IMAGE" "$PAYMENT_FRONTEND_IMAGE"; do
  [[ "$image" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+@sha256:[0-9a-f]{64}$ ]] || {
    echo "ERROR: both images must use immutable ghcr.io/owner/repository@sha256:<digest> references." >&2; exit 1;
  }
done
[[ -f .env ]] || { echo "ERROR: .env is missing on the VM." >&2; exit 1; }
[[ -s .secrets/jwt-private.pem && -s .secrets/jwt-public.pem ]] || { echo "ERROR: deployment JWT secrets are missing." >&2; exit 1; }
COMPOSE=(docker compose --env-file .env --profile deployment)
"${COMPOSE[@]}" config --quiet

echo "Starting infrastructure services..."
docker compose --env-file .env up -d postgres redis kafka jaeger prometheus grafana
echo "Waiting for PostgreSQL health..."
DB_HEALTH=""
for attempt in $(seq 1 60); do
  DB_HEALTH="$(docker inspect --format '{{.State.Health.Status}}' payment-platform-postgres 2>/dev/null || true)"
  [[ "$DB_HEALTH" == "healthy" ]] && break
  sleep 5
done
[[ "$DB_HEALTH" == "healthy" ]] || { echo "ERROR: PostgreSQL did not become healthy." >&2; exit 1; }
PREVIOUS_IMAGE="$(docker inspect --format '{{.Config.Image}}' payment-platform-api 2>/dev/null || true)"
PREVIOUS_FRONTEND_IMAGE="$(docker inspect --format '{{.Config.Image}}' payment-platform-frontend 2>/dev/null || true)"
if [[ -n "$PREVIOUS_IMAGE" ]]; then
  install -d -m 700 .deployment
  printf '%s' "$PREVIOUS_IMAGE" > .deployment/previous-image
  chmod 600 .deployment/previous-image
fi
if [[ -n "$PREVIOUS_FRONTEND_IMAGE" ]]; then
  install -d -m 700 .deployment
  printf '%s' "$PREVIOUS_FRONTEND_IMAGE" > .deployment/previous-frontend-image
  chmod 600 .deployment/previous-frontend-image
fi
./scripts/backup-postgres-linux.sh
export PAYMENT_API_IMAGE PAYMENT_FRONTEND_IMAGE
echo "Pulling immutable API and frontend images."
"${COMPOSE[@]}" pull payment-api frontend
echo "Deploying application..."
"${COMPOSE[@]}" up -d --no-build --pull always payment-api frontend frontend-proxy
PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health/readiness"
FRONTEND_HEALTH_URL="http://127.0.0.1:${FRONTEND_HTTP_PORT:-8088}/healthz"
HEALTHY=false
for attempt in $(seq 1 36); do
  if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' && curl --silent --show-error --fail --max-time 5 "$FRONTEND_HEALTH_URL" | grep -q ok; then HEALTHY=true; break; fi
  sleep 5
done
if [[ "$HEALTHY" != "true" ]]; then
  echo "ERROR: API readiness or frontend proxy health check failed." >&2
  if [[ -n "$PREVIOUS_IMAGE" && -n "$PREVIOUS_FRONTEND_IMAGE" ]]; then
    echo "Attempting application and frontend image rollback."
    export PAYMENT_API_IMAGE="$PREVIOUS_IMAGE"
    export PAYMENT_FRONTEND_IMAGE="$PREVIOUS_FRONTEND_IMAGE"
    "${COMPOSE[@]}" up -d --no-build --pull always payment-api frontend frontend-proxy || true
    for attempt in $(seq 1 24); do
      if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' && curl --silent --show-error --fail --max-time 5 "$FRONTEND_HEALTH_URL" | grep -q ok; then
        echo "Previous API and frontend images are healthy again."; exit 1
      fi
      sleep 5
    done
    echo "ERROR: rollback attempted but readiness was not restored." >&2
  else
    echo "No previous image available for automatic rollback." >&2
  fi
  exit 1
fi
install -d -m 700 .deployment
printf '%s' "$PAYMENT_API_IMAGE" > .deployment/current-image
printf '%s' "$PAYMENT_FRONTEND_IMAGE" > .deployment/current-frontend-image
chmod 600 .deployment/current-image .deployment/current-frontend-image
echo "Deployment succeeded: $PAYMENT_API_IMAGE"
echo "Readiness endpoint: $HEALTH_URL"
echo "Frontend URL (behind TLS reverse proxy): http://127.0.0.1:${FRONTEND_HTTP_PORT:-8088}"

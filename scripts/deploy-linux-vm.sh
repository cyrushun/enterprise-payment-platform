#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
: "${PAYMENT_API_IMAGE:?Set PAYMENT_API_IMAGE to an immutable GHCR image reference}"
[[ "$PAYMENT_API_IMAGE" =~ ^ghcr\.io/[a-z0-9._-]+/[a-z0-9._-]+@sha256:[0-9a-f]{64}$ ]] || {
  echo "ERROR: image must be ghcr.io/owner/repository@sha256:<64-character-image-digest>." >&2; exit 1;
}
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
if [[ -n "$PREVIOUS_IMAGE" ]]; then
  install -d -m 700 .deployment
  printf '%s' "$PREVIOUS_IMAGE" > .deployment/previous-image
  chmod 600 .deployment/previous-image
fi
./scripts/backup-postgres-linux.sh
export PAYMENT_API_IMAGE
echo "Pulling immutable image: $PAYMENT_API_IMAGE"
"${COMPOSE[@]}" pull payment-api
echo "Deploying application..."
"${COMPOSE[@]}" up -d --no-build --pull always payment-api
PORT="${SERVER_PORT:-8080}"
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health/readiness"
HEALTHY=false
for attempt in $(seq 1 36); do
  if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then HEALTHY=true; break; fi
  sleep 5
done
if [[ "$HEALTHY" != "true" ]]; then
  echo "ERROR: new application did not become ready." >&2
  if [[ -n "$PREVIOUS_IMAGE" ]]; then
    echo "Attempting application-image rollback to $PREVIOUS_IMAGE"
    export PAYMENT_API_IMAGE="$PREVIOUS_IMAGE"
    "${COMPOSE[@]}" up -d --no-build --pull always payment-api || true
    for attempt in $(seq 1 24); do
      if curl --silent --show-error --fail --max-time 5 "$HEALTH_URL" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
        echo "Previous application image is healthy again."; exit 1
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
chmod 600 .deployment/current-image
echo "Deployment succeeded: $PAYMENT_API_IMAGE"
echo "Readiness endpoint: $HEALTH_URL"

#!/usr/bin/env bash
set -Eeuo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"
[[ -f .env ]] || { echo "ERROR: .env is required on this VM." >&2; exit 1; }
[[ -f observability/prometheus/prometheus.yml.template ]] || { echo "ERROR: Prometheus template is missing." >&2; exit 1; }
# Read the required simple KEY=VALUE entries without sourcing/evaluating .env.
env_value() {
  local key="$1" line value
  line="$(grep -E "^${key}=" .env | tail -n 1 || true)"
  [[ -n "$line" ]] || return 1
  value="${line#*=}"
  if [[ "${value:0:1}" == '"' && "${value: -1}" == '"' ]]; then value="${value:1:-1}"; fi
  if [[ "${value:0:1}" == "'" && "${value: -1}" == "'" ]]; then value="${value:1:-1}"; fi
  printf '%s' "$value"
}
POSTGRES_DB="$(env_value POSTGRES_DB)"
POSTGRES_USER="$(env_value POSTGRES_USER)"
POSTGRES_PASSWORD="$(env_value POSTGRES_PASSWORD)"
METRICS_USERNAME="$(env_value METRICS_USERNAME)"
METRICS_PASSWORD="$(env_value METRICS_PASSWORD)"
for required in POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD METRICS_USERNAME METRICS_PASSWORD; do
  if [[ -z "${!required}" ]]; then echo "ERROR: ${required} is empty in .env." >&2; exit 1; fi
done
PROMETHEUS_TARGET="$(env_value PROMETHEUS_TARGET || printf payment-api:8080)"
[[ "$PROMETHEUS_TARGET" =~ ^[A-Za-z0-9_.:-]+$ ]] || { echo "ERROR: invalid PROMETHEUS_TARGET." >&2; exit 1; }
[[ "$METRICS_USERNAME" =~ ^[A-Za-z0-9_.-]+$ ]] || { echo "ERROR: invalid METRICS_USERNAME." >&2; exit 1; }
install -d -m 700 .secrets
sed -e "s/__METRICS_USERNAME__/${METRICS_USERNAME}/g" \
    -e "s/__PROMETHEUS_TARGET__/${PROMETHEUS_TARGET}/g" \
    observability/prometheus/prometheus.yml.template > observability/prometheus/prometheus.yml
printf '%s' "$METRICS_PASSWORD" > .secrets/metrics-password
chmod 0644 .secrets/metrics-password
[[ -s .secrets/jwt-private.pem && -s .secrets/jwt-public.pem ]] || {
  echo "ERROR: matching deployment JWT key files are required in .secrets/." >&2; exit 1;
}
echo "Linux deployment configuration prepared; secret values were not displayed."
echo "Prometheus target: ${PROMETHEUS_TARGET}"

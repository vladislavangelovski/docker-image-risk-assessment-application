#!/usr/bin/env bash
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-240}"
INGEST_STARTUP_ENABLED="${INGEST_STARTUP_ENABLED:-false}"

export INGEST_STARTUP_ENABLED

cleanup() {
  docker compose down -v --remove-orphans
  if [[ -f .env.ci.generated ]]; then
    rm -f .env
    rm -f .env.ci.generated
  fi
}

trap cleanup EXIT

if [[ ! -f .env ]]; then
  cat > .env <<'EOF'
POSTGRES_USER=risk
POSTGRES_PASSWORD=risk
POSTGRES_DB=riskdb
EOF
  touch .env.ci.generated
fi

echo "Starting compose stack..."
docker compose up -d --build

echo "Waiting for gateway health..."
start_ts=$(date +%s)
while true; do
  status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/actuator/health" || true)
  if [[ "$status" == "200" ]]; then
    break
  fi
  now_ts=$(date +%s)
  if (( now_ts - start_ts > MAX_WAIT_SECONDS )); then
    echo "Gateway health did not become ready in ${MAX_WAIT_SECONDS}s."
    docker compose ps
    docker compose logs --no-color gateway-service || true
    exit 1
  fi
  sleep 5
done

echo "Smoke check: CVE list endpoint..."
status=$(curl -s -o /dev/null -w "%{http_code}" "$GATEWAY_URL/api/v1/cves?page=0&size=1" || true)
if [[ "$status" != "200" ]]; then
  echo "CVE list returned status $status"
  exit 1
fi

echo "Compose smoke test passed."

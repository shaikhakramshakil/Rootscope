#!/usr/bin/env bash
# Reproduces the PRD scenario against a local backend (default localhost:8080).
# Usage: ./seed.sh [base-url]
set -euo pipefail
BASE="${1:-http://localhost:8080}"

post() { curl -sf -H 'Content-Type: application/json' -d "$2" "$BASE$1"; }

post /api/v1/dependencies '{"from":"frontend","to":"checkout"}' > /dev/null
post /api/v1/dependencies '{"from":"checkout","to":"payment"}' > /dev/null
post /api/v1/dependencies '{"from":"payment","to":"database"}' > /dev/null

post /api/v1/deployments \
  '{"service":"payment","version":"v4","commit":"abc123","author":"akram",
    "timestamp":"2026-09-12T14:31:00Z"}' > /dev/null

post /api/v1/anomalies \
  '{"service":"payment","type":"DB_LATENCY","severity":0.7,
    "timestamp":"2026-09-12T14:32:00Z",
    "payload":"{\"metric\":\"db_latency_ms\",\"errorRateBefore\":0.01,\"errorRateAfter\":0.02}"}' \
  > /dev/null

post /api/v1/anomalies \
  '{"service":"payment","type":"ERROR_SPIKE","severity":0.9,
    "timestamp":"2026-09-12T14:33:00Z",
    "payload":"{\"errorRateBefore\":0.01,\"errorRateAfter\":0.05}"}' > /dev/null

post /api/v1/incidents \
  '{"title":"Checkout failures","service":"checkout","severity":"HIGH",
    "startedAt":"2026-09-12T14:34:00Z"}' \
  | python3 -m json.tool

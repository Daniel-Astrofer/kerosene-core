#!/bin/sh
set -eu

LND_HOST="${LND_HOST:-lnd-bitcoind}"
LND_REST_PORT="${LND_REST_PORT:-8080}"
LND_WALLET_PASSWORD="${LND_WALLET_PASSWORD:-}"
LND_BOOTSTRAP_TIMEOUT_SECONDS="${LND_BOOTSTRAP_TIMEOUT_SECONDS:-180}"
LND_BOOTSTRAP_WATCH="${LND_BOOTSTRAP_WATCH:-false}"
LND_BOOTSTRAP_WATCH_INTERVAL_SECONDS="${LND_BOOTSTRAP_WATCH_INTERVAL_SECONDS:-30}"
LND_TLS_SERVER_NAME="${LND_TLS_SERVER_NAME:-$LND_HOST}"
LND_DIR="${LND_DIR:-/lnd}"
LND_CERT="$LND_DIR/tls.cert"
BASE_URL="https://$LND_HOST:$LND_REST_PORT"

if [ -z "$LND_WALLET_PASSWORD" ]; then
  echo "[lnd-bootstrap][warn] LND_WALLET_PASSWORD is missing; skipping wallet bootstrap." >&2
  exit 0
fi

password_b64="$(printf '%s' "$LND_WALLET_PASSWORD" | base64 | tr -d '\n')"

wait_for_rest() {
  deadline=$(( $(date +%s) + LND_BOOTSTRAP_TIMEOUT_SECONDS ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    if [ -s "$LND_CERT" ] && curl --silent --show-error --output /dev/null --max-time 3 --cacert "$LND_CERT" "$BASE_URL/v1/state"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

request() {
  method="$1"
  path="$2"
  data="${3:-}"
  if [ -n "$data" ]; then
    curl --silent --show-error --write-out '\n%{http_code}' --max-time 10 --cacert "$LND_CERT" \
      --request "$method" "$BASE_URL$path" \
      --header "Content-Type: application/json" \
      --data "$data" || true
  else
    curl --silent --show-error --write-out '\n%{http_code}' --max-time 10 --cacert "$LND_CERT" \
      --request "$method" "$BASE_URL$path" || true
  fi
}

body_from_response() {
  printf '%s\n' "$1" | sed '$d'
}

status_from_response() {
  printf '%s\n' "$1" | tail -n 1
}

wallet_is_unlocked() {
  response="$(request GET /v1/getinfo)"
  status="$(status_from_response "$response")"
  [ "$status" = "200" ]
}

unlock_wallet() {
  response="$(request POST /v1/unlockwallet "{\"wallet_password\":\"$password_b64\"}")"
  status="$(status_from_response "$response")"
  body="$(body_from_response "$response")"
  [ -n "$body" ] && printf '%s\n' "$body"

  case "$status" in
    2??) return 0 ;;
    *)
      if printf '%s\n' "$body" | grep -Eiq 'already unlocked|wallet unlocked'; then
        return 0
      fi
      return 1
      ;;
  esac
}

init_wallet() {
  response="$(request POST /v1/initwallet "{\"wallet_password\":\"$password_b64\"}")"
  status="$(status_from_response "$response")"
  body="$(body_from_response "$response")"
  [ -n "$body" ] && printf '%s\n' "$body"

  case "$status" in
    2??) return 0 ;;
    *)
      if printf '%s\n' "$body" | grep -Eiq 'already exists|wallet exists|wallet already'; then
        return 2
      fi
      return 1
      ;;
  esac
}

bootstrap_once() {
  if ! wait_for_rest; then
    echo "[lnd-bootstrap][warn] LND REST did not become reachable in time." >&2
    return 0
  fi

  if wallet_is_unlocked; then
    echo "[lnd-bootstrap] LND wallet is already unlocked."
    return 0
  fi

  echo "[lnd-bootstrap] Initializing or unlocking LND wallet."
  set +e
  init_wallet
  init_status="$?"
  set -e
  if [ "$init_status" = "0" ]; then
    echo "[lnd-bootstrap] LND wallet bootstrap completed."
    return 0
  fi

  if [ "$init_status" = "2" ] && unlock_wallet; then
    echo "[lnd-bootstrap] LND wallet unlock completed."
    return 0
  fi

  echo "[lnd-bootstrap][warn] LND wallet bootstrap could not complete yet." >&2
  return 0
}

if [ "$LND_BOOTSTRAP_WATCH" = "true" ]; then
  while true; do
    bootstrap_once
    sleep "$LND_BOOTSTRAP_WATCH_INTERVAL_SECONDS"
  done
fi

bootstrap_once

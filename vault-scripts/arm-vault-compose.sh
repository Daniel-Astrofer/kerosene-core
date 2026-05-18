#!/bin/sh
set -eu

VAULT_URL="${VAULT_URL:-http://kerosene-vault:8090/v1/vault/arm}"
MAX_WAIT="${VAULT_ARM_MAX_WAIT_SECONDS:-120}"
DIRECTORS="${VAULT_ARM_DIRECTORS:-director-1,director-2}"
MASTER_KEY="${AES_SECRET:-}"

if [ -z "$MASTER_KEY" ]; then
  echo "[vault][warn] AES_SECRET is missing; skipping local Vault arm." >&2
  exit 0
fi

deadline=$(( $(date +%s) + MAX_WAIT ))
while ! curl --silent --show-error --output /dev/null --max-time 3 "$VAULT_URL"; do
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "[vault][warn] Timed out waiting for Vault arm endpoint." >&2
    exit 0
  fi
  sleep 2
done

submit_approval() {
  director="$1"
  signature="SIGNATURE_${director}_LOCAL_DEV"
  response="$(curl \
    --silent \
    --show-error \
    --write-out '\n%{http_code}' \
    --request POST "$VAULT_URL" \
    --header "X-Director-Id: $director" \
    --header "X-Director-Signature: $signature" \
    --header "Content-Type: application/json" \
    --data "{\"master_key\":\"$MASTER_KEY\"}")"
  status="$(printf '%s\n' "$response" | tail -n 1)"
  body="$(printf '%s\n' "$response" | sed '$d')"
  [ -n "$body" ] && printf '%s\n' "$body"

  case "$status" in
    2??) return 0 ;;
    400)
      if printf '%s\n' "$body" | grep -qi 'already armed'; then
        return 0
      fi
      ;;
  esac

  echo "[vault][warn] Vault arm request for $director failed with HTTP $status." >&2
  return 1
}

OLD_IFS="$IFS"
IFS=","
set -- $DIRECTORS
IFS="$OLD_IFS"

for director in "$@"; do
  echo "[vault] Submitting quorum approval from $director..."
  submit_approval "$director" || exit 0
done

echo "[vault] Compose arm flow completed."

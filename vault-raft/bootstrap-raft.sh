#!/bin/sh
set -eu

BOOTSTRAP_DIR="${VAULT_BOOTSTRAP_DIR:-/vault/bootstrap}"
KEYS_FILE="$BOOTSTRAP_DIR/unseal-keys"
ROOT_TOKEN_FILE="$BOOTSTRAP_DIR/root-token"
APP_TOKEN_FILE="$BOOTSTRAP_DIR/app-health-token"

mkdir -p "$BOOTSTRAP_DIR"

wait_for_vault() {
  addr="$1"
  export VAULT_ADDR="$addr"
  i=0
  while [ "$i" -lt 120 ]; do
    set +e
    vault status >/tmp/vault-status.txt 2>&1
    code="$?"
    set -e
    if [ "$code" -eq 0 ] || [ "$code" -eq 2 ]; then
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "Timed out waiting for $addr" >&2
  cat /tmp/vault-status.txt >&2 || true
  exit 1
}

is_initialized() {
  grep -Eq '^Initialized[[:space:]]+true' /tmp/vault-status.txt
}

is_sealed() {
  grep -Eq '^Sealed[[:space:]]+true' /tmp/vault-status.txt
}

unseal_if_needed() {
  addr="$1"
  wait_for_vault "$addr"
  if ! is_sealed; then
    return 0
  fi

  while IFS= read -r key; do
    [ -n "$key" ] || continue
    vault operator unseal "$key" >/dev/null || true
    wait_for_vault "$addr"
    is_sealed || return 0
  done < "$KEYS_FILE"

  echo "Vault at $addr is still sealed after applying available unseal keys." >&2
  exit 1
}

wait_for_vault "http://vault-raft-1:8200"

if ! is_initialized; then
  vault operator init -key-shares=3 -key-threshold=2 > "$BOOTSTRAP_DIR/init.txt"
  awk -F': ' '/Unseal Key [0-9]+:/ {print $2}' "$BOOTSTRAP_DIR/init.txt" > "$KEYS_FILE"
  awk -F': ' '/Initial Root Token:/ {print $2}' "$BOOTSTRAP_DIR/init.txt" > "$ROOT_TOKEN_FILE"
fi

if [ ! -s "$ROOT_TOKEN_FILE" ]; then
  echo "Vault is initialized, but $ROOT_TOKEN_FILE is missing." >&2
  exit 1
fi

ROOT_TOKEN="$(cat "$ROOT_TOKEN_FILE")"

if [ ! -s "$KEYS_FILE" ]; then
  wait_for_vault "http://vault-raft-1:8200"
  if is_sealed; then
    echo "Vault is sealed, but $KEYS_FILE is missing." >&2
    exit 1
  fi
else
  unseal_if_needed "http://vault-raft-1:8200"
fi

export VAULT_ADDR="http://vault-raft-2:8200"
vault operator raft join "http://vault-raft-1:8200" >/dev/null 2>&1 || true
if [ -s "$KEYS_FILE" ]; then
  unseal_if_needed "http://vault-raft-2:8200"
fi

export VAULT_ADDR="http://vault-raft-3:8200"
vault operator raft join "http://vault-raft-1:8200" >/dev/null 2>&1 || true
if [ -s "$KEYS_FILE" ]; then
  unseal_if_needed "http://vault-raft-3:8200"
fi

export VAULT_ADDR="http://vault-raft-1:8200"
export VAULT_TOKEN="$ROOT_TOKEN"
printf '%s\n' "$ROOT_TOKEN" > "$APP_TOKEN_FILE"

chmod 0640 "$KEYS_FILE" "$ROOT_TOKEN_FILE" "$APP_TOKEN_FILE" 2>/dev/null || true
chgrp "${VAULT_APP_READ_GID:-65532}" "$APP_TOKEN_FILE" 2>/dev/null || true

vault status >/dev/null

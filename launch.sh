#!/usr/bin/env bash
# ============================================================
# launch.sh  —  Kerosene Hydra [v4.0] (Windows / WSL / macOS)
# ============================================================

set -uo pipefail
cd "$(dirname "$0")"

BUILD=false
DOWN=false

for arg in "$@"; do
  case "$arg" in
    --build) BUILD=true ;;
    --down)  DOWN=true  ;;
  esac
done

# ── Stop ─────────────────────────────────────────────────────────
if $DOWN; then
  echo "[Kerosene] Stopping all containers..."
  docker compose down --remove-orphans --volumes
  exit 0
fi

# ── mTLS Certificate Guard ────────────────────────────────────────────────────
# CRITICAL: In a multi-node setup (Iceland + Singapore + Switzerland), the CA
# MUST be generated exactly ONCE in an air-gapped environment and DISTRIBUTED
# to all nodes. If each node generates its own CA, nodes will not trust each other
# and quorum mTLS will fail completely.
#
# This guard:
#  1. In PRODUCTION mode → BLOCKS startup if certs are not pre-provisioned.
#  2. In DEV mode → Generates certs locally (single-node only).
# ─────────────────────────────────────────────────────────────────────────────
ENV_MODE="${KEROSENE_ENV:-dev}"

if [ "$ENV_MODE" = "production" ]; then
  # ── Production: Certs must be pre-distributed from the air-gapped CA host ──
  if [ ! -d "../../certs" ] || [ ! -f "../../certs/rootCA.crt" ]; then
    echo "[KEROSENE] ❌ FATAL: Production mode requires pre-provisioned mTLS certificates."
    echo "           Certs must be generated ONCE on an air-gapped machine and distributed"
    echo "           to all nodes. DO NOT generate them locally on each shard."
    echo "           See: docs/CERT_PROVISIONING.md for the procedure."
    exit 1
  fi
  echo "[Kerosene] ✅ mTLS certificates verified (production, pre-distributed)."
else
  # ── Dev/Test: Generate locally for single-node testing ────────────────────
  if [ ! -d "../../certs" ]; then
    echo "[Kerosene] DEV MODE: mTLS certificates not found. Generating for local testing..."
    echo "           ⚠️  WARNING: These certs are for single-node dev ONLY."
    echo "           ⚠️  For multi-node production, use a shared air-gapped CA."
    chmod +x ../../cert-generator.sh
    ../../cert-generator.sh
  else
    echo "[Kerosene] DEV MODE: Existing certs found — skipping cert generation."
  fi
fi

# ── Start stack ───────────────────────────────────────────────────
if $BUILD; then
  echo "[Kerosene] Building images and starting stack..."
  docker compose up --build -d
else
  echo "[Kerosene] Starting stack..."
  docker compose up -d
fi

if [ $? -ne 0 ]; then
  echo "[Kerosene] ERROR: Docker compose failed."
  exit 1
fi

echo "[Kerosene] Stack is up. Initializing terminals..."
sleep 1

# ── Detect Environment ────────────────────────────────────────────
IS_WSL=false
[[ -n "${WSLENV:-}" || -f /proc/sys/fs/binfmt_misc/WSLInterop ]] && IS_WSL=true

# ── WSL Dashboard (Simplified) ──────────────────────────────────
if $IS_WSL; then
  echo "[Kerosene] Stack is operational. Streaming logs..."
  docker compose logs -f
  exit 0
fi

# ── macOS — Terminal.app ──────────────────────────────────────────
if [[ "$(uname)" == "Darwin" ]]; then
  cmd="docker compose logs -f"
  osascript - "Kerosene External Logs" "$cmd" <<'APPLESCRIPT'
on run {title, cmd}
  tell application "Terminal"
    do script cmd
    set custom title of front window to title
    activate
  end tell
run
APPLESCRIPT
  exit 0
fi

# ── fallback basic monitor ───────────────────────────────────────
echo "[Kerosene] No GUI terminal logic applies. Showing logs locally..."
docker compose logs -f

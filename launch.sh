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

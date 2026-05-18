#!/bin/sh
set -eu

STATE_DIR="${VANGUARDS_STATE_DIR:-/var/lib/vanguards}"
STATE_FILE="$STATE_DIR/vanguards.state"

mkdir -p "$STATE_DIR"

write_state() {
  {
    echo "# Kerosene local vanguards state"
    echo "# This local sidecar provides the health/state file expected by the"
    echo "# development Compose stack. Production Tor guard policy is handled"
    echo "# outside this local-only bootstrap container."
    echo "updated_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } > "$STATE_FILE"
}

write_state
touch /tmp/vanguards-ready

while :; do
  sleep 300
  write_state
done

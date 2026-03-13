#!/usr/bin/env bash
# =============================================================
# mpc-sidecar-entrypoint.sh — SGX Hardware Guard
# =============================================================
# This script is the entrypoint for the MPC sidecar process.
# It enforces that the process ONLY runs on real SGX hardware.
# It will REFUSE to start in simulation mode to prevent
# accidental key operations outside the trusted enclave.
# =============================================================

set -euo pipefail

ENCLAVE_MODE="${ENCLAVE_MODE:-HARDWARE_ENCLAVE}"

echo "[MPC Sidecar] Starting with ENCLAVE_MODE=${ENCLAVE_MODE}"

# ── SGX Hardware Verification ──────────────────────────────────────
if [ "${ENCLAVE_MODE}" = "HARDWARE_ENCLAVE" ]; then
    if [ ! -e /dev/isgx ] && [ ! -e /dev/sgx/enclave ]; then
        echo ""
        echo "╔══════════════════════════════════════════════════════════╗"
        echo "║  FATAL: SGX hardware device not detected.               ║"
        echo "║  Expected: /dev/isgx or /dev/sgx/enclave                ║"
        echo "║  ENCLAVE_MODE is set to HARDWARE_ENCLAVE.               ║"
        echo "║                                                          ║"
        echo "║  Refusing to fall back to simulation mode.              ║"
        echo "║  This would expose private key operations outside        ║"
        echo "║  a trusted hardware enclave — a critical security risk. ║"
        echo "║                                                          ║"
        echo "║  To resolve:                                             ║"
        echo "║    1. Run on bare-metal with SGX-capable CPU and BIOS   ║"
        echo "║       SGX enabled.                                      ║"
        echo "║    2. Install Intel SGX DCAP/OOT drivers.               ║"
        echo "║    3. Verify: ls -la /dev/isgx /dev/sgx                 ║"
        echo "║                                                          ║"
        echo "║  If you INTENTIONALLY want simulation for local dev,    ║"
        echo "║  set ENCLAVE_MODE=SIMULATION in your environment.       ║"
        echo "║  NEVER use SIMULATION in production.                    ║"
        echo "╚══════════════════════════════════════════════════════════╝"
        echo ""
        exit 1
    fi

    # Verify SGX device is accessible (not just present)
    if [ -e /dev/isgx ] && [ ! -r /dev/isgx ]; then
        echo "[MPC Sidecar] FATAL: /dev/isgx exists but is not readable. Check permissions (sgx group)."
        exit 1
    fi

    echo "[MPC Sidecar] ✅ SGX hardware device detected. Proceeding with hardware enclave mode."

elif [ "${ENCLAVE_MODE}" = "SIMULATION" ]; then
    echo ""
    echo "┌─────────────────────────────────────────────────────────┐"
    echo "│  ⚠️  WARNING: Running in SGX SIMULATION mode.           │"
    echo "│  Private key operations are NOT protected by hardware.  │"
    echo "│  This mode is ONLY for local development.               │"
    echo "│  NEVER deploy in this mode to production.               │"
    echo "└─────────────────────────────────────────────────────────┘"
    echo ""
else
    echo "[MPC Sidecar] FATAL: Unknown ENCLAVE_MODE '${ENCLAVE_MODE}'. Valid values: HARDWARE_ENCLAVE, SIMULATION"
    exit 1
fi

# ── Launch MPC sidecar process ────────────────────────────────────
# Replace this with the actual binary invocation, e.g.:
#   exec /app/mpc-sidecar --config /etc/mpc/config.toml
echo "[MPC Sidecar] Handing off to MPC sidecar binary..."
exec "$@"

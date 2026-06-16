#!/usr/bin/env bash
set -euo pipefail

echo "[CRITICAL] Kerosene ephemeral shutdown protocol triggered."

pkill -9 java || true
pkill -9 mpc_enclave || true
umount -f /mnt/mpc-shards || true

echo 1 > /proc/sys/kernel/sysrq
echo o > /proc/sysrq-trigger

poweroff -f

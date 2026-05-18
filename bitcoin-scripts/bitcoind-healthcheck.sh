#!/bin/sh
set -eu

DATA_DIR="${BITCOIN_DATA_DIR:-/home/bitcoin/.bitcoin}"
CHAIN="${BITCOIN_CHAIN:-mainnet}"
RPC_USER="${BITCOIN_RPC_USER:-kerosene}"
RPC_PASSWORD="${BITCOIN_RPC_PASSWORD:?BITCOIN_RPC_PASSWORD is required}"
RPC_WALLET="${BITCOIN_RPC_WALLET:-kerosene}"

CHAIN_ARGS=""
case "$CHAIN" in
  mainnet|main) CHAIN_ARGS="" ;;
  testnet|testnet3) CHAIN_ARGS="-testnet" ;;
  testnet4) CHAIN_ARGS="-testnet4" ;;
  signet) CHAIN_ARGS="-signet" ;;
  regtest) CHAIN_ARGS="-regtest" ;;
esac

CLI_BIN="$(command -v bitcoin-cli || true)"
if [ -z "$CLI_BIN" ]; then
  exit 1
fi

"$CLI_BIN" -datadir="$DATA_DIR" $CHAIN_ARGS \
  -rpcuser="$RPC_USER" \
  -rpcpassword="$RPC_PASSWORD" \
  getblockchaininfo >/dev/null

"$CLI_BIN" -datadir="$DATA_DIR" $CHAIN_ARGS \
  -rpcuser="$RPC_USER" \
  -rpcpassword="$RPC_PASSWORD" \
  -rpcwallet="$RPC_WALLET" \
  getwalletinfo >/dev/null 2>&1 || true

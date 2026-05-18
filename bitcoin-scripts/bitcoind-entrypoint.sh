#!/bin/sh
set -eu

DATA_DIR="${BITCOIN_DATA_DIR:-/home/bitcoin/.bitcoin}"
CHAIN="${BITCOIN_CHAIN:-mainnet}"
RPC_USER="${BITCOIN_RPC_USER:-kerosene}"
RPC_PASSWORD="${BITCOIN_RPC_PASSWORD:?BITCOIN_RPC_PASSWORD is required}"
RPC_WALLET="${BITCOIN_RPC_WALLET:-kerosene}"
PRUNE_MB="${BITCOIN_PRUNE_MB:-550}"
MAX_MEMPOOL_MB="${BITCOIN_MAX_MEMPOOL_MB:-64}"
DBCACHE_MB="${BITCOIN_DBCACHE_MB:-64}"
P2P_PORT="${BITCOIN_P2P_PORT:-8333}"

mkdir -p "$DATA_DIR"

CONFIG_FILE="$DATA_DIR/bitcoin.conf"
cat > "$CONFIG_FILE" <<EOF
server=1
listen=1
rpcbind=0.0.0.0
rpcallowip=0.0.0.0/0
rpcuser=$RPC_USER
rpcpassword=$RPC_PASSWORD
wallet=$RPC_WALLET
prune=$PRUNE_MB
maxmempool=$MAX_MEMPOOL_MB
dbcache=$DBCACHE_MB
zmqpubrawtx=tcp://0.0.0.0:28332
zmqpubhashblock=tcp://0.0.0.0:28333
zmqpubrawblock=tcp://0.0.0.0:28334
port=$P2P_PORT
EOF

CHAIN_ARGS=""
case "$CHAIN" in
  mainnet|main) CHAIN_ARGS="" ;;
  testnet|testnet3) CHAIN_ARGS="-testnet" ;;
  testnet4) CHAIN_ARGS="-testnet4" ;;
  signet) CHAIN_ARGS="-signet" ;;
  regtest) CHAIN_ARGS="-regtest" ;;
  *) echo "Unsupported BITCOIN_CHAIN=$CHAIN" >&2; exit 1 ;;
esac

REINDEX_ARGS=""
if [ "${BITCOIN_REINDEX_ONCE:-false}" = "true" ]; then
  REINDEX_ARGS="$REINDEX_ARGS -reindex"
fi
if [ "${BITCOIN_REINDEX_CHAINSTATE_ONCE:-false}" = "true" ]; then
  REINDEX_ARGS="$REINDEX_ARGS -reindex-chainstate"
fi

BITCOIND_BIN="$(command -v bitcoind || command -v bitcoin-node || true)"
if [ -z "$BITCOIND_BIN" ]; then
  echo "bitcoind binary not found in image." >&2
  exit 127
fi

exec "$BITCOIND_BIN" -datadir="$DATA_DIR" $CHAIN_ARGS $REINDEX_ARGS

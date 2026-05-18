#!/bin/sh
set -eu

network="${BITCOIN_NETWORK:-mainnet}"
case "$network" in
  mainnet|bitcoin) network_flag="--bitcoin.mainnet" ;;
  testnet|testnet3) network_flag="--bitcoin.testnet" ;;
  testnet4) network_flag="--bitcoin.testnet4" ;;
  signet) network_flag="--bitcoin.signet" ;;
  regtest) network_flag="--bitcoin.regtest" ;;
  simnet) network_flag="--bitcoin.simnet" ;;
  *)
    echo "[lnd][warn] Unknown BITCOIN_NETWORK '$network'; defaulting to mainnet." >&2
    network_flag="--bitcoin.mainnet"
    ;;
esac

set -- lnd \
  --bitcoin.active \
  "$network_flag" \
  "--bitcoin.node=${LND_BITCOIN_NODE:-bitcoind}" \
  "--rpclisten=0.0.0.0:${LND_GRPC_PORT:-10009}" \
  "--restlisten=0.0.0.0:${LND_REST_PORT:-8080}" \
  "--alias=${LND_ALIAS:-kerosene-lnd-bitcoind}" \
  "--color=${LND_COLOR:-#D1495B}" \
  "--bitcoind.rpchost=${BITCOIN_RPC_HOST:-bitcoin-pruned-node}:${BITCOIN_RPC_PORT:-8332}" \
  "--bitcoind.rpcuser=${BITCOIN_RPC_USER:-kerosene}" \
  "--bitcoind.rpcpass=${BITCOIN_RPC_PASSWORD:-}" \
  "--bitcoind.zmqpubrawtx=${BITCOIN_ZMQ_RAWTX:-tcp://bitcoin-pruned-node:28332}" \
  "--bitcoind.zmqpubrawblock=${BITCOIN_ZMQ_RAWBLOCK:-tcp://bitcoin-pruned-node:28334}"

if [ -n "${LND_TLS_EXTRA_DOMAINS:-}" ]; then
  for domain in $(printf '%s' "$LND_TLS_EXTRA_DOMAINS" | tr ',' ' '); do
    set -- "$@" "--tlsextradomain=$domain"
  done
fi

if [ -n "${LND_TLS_EXTRA_IPS:-}" ]; then
  for ip in $(printf '%s' "$LND_TLS_EXTRA_IPS" | tr ',' ' '); do
    set -- "$@" "--tlsextraip=$ip"
  done
fi

if [ -n "${LND_EXTERNAL_IP:-}" ]; then
  set -- "$@" "--externalip=$LND_EXTERNAL_IP"
fi

echo "[lnd] Starting LND on $network with bitcoind backend ${BITCOIN_RPC_HOST:-bitcoin-pruned-node}:${BITCOIN_RPC_PORT:-8332}."
exec "$@"

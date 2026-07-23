# KFE Testnet Runtime

Use when deposit/withdraw must hit Bitcoin testnet (not mock credit).

Treasury signing on local-full/testnet beta uses **vault mesh** (`vault-mesh-lab` / `kfe.vaultmesh.*`, mesh-only). mpc-sidecar signing stays off.

```env
BITCOIN_NETWORK=testnet
BITCOIN_RPC_ENABLED=true
BITCOIN_RPC_REQUIRED=true
BITCOIN_RPC_URL=http://bitcoin-core:8332
BITCOIN_RPC_USER=<rpc-user>
BITCOIN_RPC_PASSWORD=<rpc-password>
BITCOIN_RPC_WALLET=kerosene
KFE_RECEIVE_BITCOIN_CORE_WALLET_ADDRESS_ENABLED=true
KFE_NETWORK_MONITOR_ENABLED=true
KFE_BITCOIN_VALIDATE_NETWORK_ENABLED=true
KFE_BITCOIN_CORE_WALLETS_BOOTSTRAP_ENABLED=true
KFE_BITCOIN_CORE_FUNDS_WALLET=kerosene-funds
KFE_BITCOIN_CORE_PROFIT_WALLET=kerosene-profit
```

Mesh lab (compose) typically uses `BITCOIN_NETWORK=testnet3` + `kfe-service-vaultmesh-testnet3.properties`. App CM may still say `BITCOIN_NETWORK=testnet` (classic testnet3 / Core `chain=test`). Align before blaming rails.

## Behavior

- Boot fails if Core chain ≠ `BITCOIN_NETWORK`.
- KFE load/create Core wallets (idempotent).
- Ledger system wallets for funds + profit start at 0.
- Deposit credit only after monitor sees confirms.
- Settled tx credits `keroseneFeeSats` to profit wallet.
- No fake/instant deposit credit.
- Outbound Taproot settlement (when vaultmesh submit enabled) goes Intent → mesh Receipt, not mpc-sidecar.

Android: apps must support classic testnet (`tb1…` / Electrum testnet). Use `BITCOIN_NETWORK=testnet` (LND dir name); Core reports `chain=test`.

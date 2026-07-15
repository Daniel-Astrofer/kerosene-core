# KFE Testnet Financial Runtime

Use this profile when deposits and withdrawals must run through a Bitcoin test network instead of mocked balance injection.

Required runtime values:

```env
BITCOIN_NETWORK=testnet4
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

Runtime behavior:

- Startup fails if Bitcoin Core reports a chain different from `BITCOIN_NETWORK`.
- KFE loads or creates the configured Bitcoin Core wallets idempotently.
- KFE creates ledger system wallets for global funds and profit with zero balance.
- Deposits are credited only after the network monitor observes confirmations.
- `keroseneFeeSats` is credited to the system profit wallet when a transaction settles.
- No development balance injection or instant fake deposit credit is available.

For Android wallet testing, every app in the flow must explicitly support `testnet4`.

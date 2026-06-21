# KFE-only financial architecture

KFE is the only financial system of record in Kerosene.

Anything related to money, wallet balances, statements, financial audit, payment requests, on-chain execution, Lightning execution, cold-wallet operations, treasury liquidity, reconciliation, idempotency or financial authorization belongs inside `source.kfe`.

## Removed ownership

The broad backend must not own financial behavior through these legacy domains:

- `source.ledger`
- `source.payments`
- `source.wallet`
- `source.bitcoinaccounts`
- financial controllers/services under `source.transactions`
- financial treasury logic outside KFE

The legacy feature flag `kfe.legacy-financial.enabled` is forbidden. KFE-only means there is no runtime switch back to the old financial backend.

## Canonical API surface

The financial API surface must be expressed through KFE routes only:

- `/kfe/dashboard`
- `/kfe/wallets`
- `/kfe/wallets/names`
- `/kfe/wallets/{walletId}/addresses/rotate`
- `/kfe/wallets/{walletId}/utxos`
- `/kfe/wallets/{walletId}/cold-wallet/psbt`
- `/kfe/transactions`
- `/kfe/transactions/quote`
- `/kfe/transactions/{transactionId}`
- `/kfe/users/{receiverIdentifier}/receiving-capabilities`
- `/api/admin/kfe/audit/*`

New financial endpoints must be added under `/kfe` or `/api/admin/kfe`.

## Expurge gate

Run this before merging financial backend changes:

```bash
scripts/verify-kfe-only.sh
```

The script fails while any forbidden package, dependency, route or legacy feature flag remains in executable code.

Documentation can be checked in strict mode after the legacy docs are archived or deleted:

```bash
STRICT_DOCS=1 scripts/verify-kfe-only.sh
```

## Migration rule

Legacy financial data may be read only by one-off migrations into KFE tables. After migration, KFE tables are the only source of truth.

Migration output must be auditable through KFE records, not through resurrected legacy services.

# Kerosene Core

Java/Spring core for the Kerosene financial platform.

## Modules

- `auth-service`: Auth, sessions, notifications and public API.
- `kfe-service`: ledger, wallets, reconciliation and financial execution.
- `kerosene-shared`: shared Java infrastructure.
- `kerosene-contracts`: temporary in-repository contracts compatibility module.
- `adapters`: Bitcoin Core and Lightning rail adapters.

Services may build and release independently even while they share this
repository. The local contracts module remains during the first polyrepo phase;
it will be replaced by versioned artifacts from
[`kerosene-contracts`](https://github.com/Daniel-Astrofer/kerosene-contracts).

Extracted from `Daniel-Astrofer/Kerosene` with relevant history preserved.

The KFE can authorize its configured Vault endpoints against a verified
Vault-plane Kerosene Node roster. See
[`docs/KEROSENE_NODE_INTEGRATION.md`](docs/KEROSENE_NODE_INTEGRATION.md).

# Repository boundary

This repository is the canonical source for the Java financial domain:

- `auth-service`: authentication, sessions, notifications and the public API;
- `kfe-service`: ledger, wallets, reconciliation and financial execution;
- `kerosene-shared`: Java infrastructure shared only inside Core;
- `adapters`: rail adapters owned by the Core release cycle.

`kerosene-contracts` is a transitional compatibility module.
`Daniel-Astrofer/kerosene-contracts` is the canonical protocol source, and the
local module will be removed after Core consumes its published Java artifact.

Core must not read source files from the archived monorepo, Clients, Vault,
Node or Deploy.

# Backend API Documentation

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Arquivos

- [Admin Operations](ADMIN_OPERATIONS.md) - `9` endpoints
- [Auditoria](AUDIT.md) - `11` endpoints
- [Auth e Conta](AUTH.md) - `48` endpoints
- [Bitcoin Accounts](BITCOIN_ACCOUNTS.md) - `18` endpoints
- [Integracoes](INTEGRATIONS.md) - `1` endpoints
- [KFE](KFE.md) - `14` endpoints
- [Ledger](LEDGER.md) - `8` endpoints
- [Mining](MINING.md) - `5` endpoints
- [Notifications](NOTIFICATIONS.md) - `5` endpoints
- [Payments](PAYMENTS.md) - `4` endpoints
- [Public, Health e Web](PUBLIC_HEALTH_WEB.md) - `17` endpoints
- [Soberania e Quorum](SOVEREIGNTY.md) - `7` endpoints
- [Transactions, Network e Economy](TRANSACTIONS.md) - `28` endpoints
- [Treasury](TREASURY.md) - `1` endpoints
- [Wallet](WALLET.md) - `5` endpoints
- [DTO Schema Index](DTO_SCHEMA_INDEX.md)

## Cobertura

Entradas extraidas de controllers Spring: `178`.
Superficies runtime nao-controller adicionadas: `3`.
Entradas documentadas nos arquivos separados: `181` (`180` REST + `1` WebSocket/STOMP).
Headings REST no API_REFERENCE.md: `162` (`161` pares metodo/path unicos).

### Presentes nos arquivos separados e ausentes no API_REFERENCE.md

- `GET /actuator/health`
- `GET /actuator/health/**`
- `GET /auth/device-key/challenge`
- `GET /auth/device-key/devices`
- `GET /kfe/wallets/names`
- `GET /transactions/visualization`
- `GET /transactions/visualization/blockchain`
- `GET /transactions/visualization/lightning`
- `POST /auth/device-key/devices/{credentialId}/revoke`
- `POST /auth/device-key/onboarding/finish`
- `POST /auth/device-key/onboarding/start`
- `POST /auth/device-key/register/finish`
- `POST /auth/device-key/register/start`
- `POST /auth/device-key/verify`
- `POST /quorum/commit`
- `POST /quorum/health`
- `POST /quorum/prepare`
- `POST /transactions/visualization/blockchain/sync`

### Presentes no API_REFERENCE.md e ausentes nos arquivos separados

- Nenhum.

## Regras globais relevantes

- `Security` aplica CORS explicito, CSRF desabilitado, headers defensivos e sessao stateless.
- `EndpointPolicyRegistry` classifica endpoints como `PUBLIC`, `ADMIN` ou `AUTHENTICATED`.
- O fallback de seguranca e `anyRequest().denyAll()`.
- `ParanoidSecurityFilter`, `RateLimitFilter` e `JwtAuthenticationFilter` rodam antes dos handlers REST.
- `ReleaseAttestationFilter` pode exigir headers de atestacao quando habilitado por configuracao.

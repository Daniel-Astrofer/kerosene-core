# Configuration

## Fontes de configuracao

O backend usa tres camadas principais de configuracao:

- `src/main/resources/application.properties`: baseline local/default
- `src/main/resources/application-docker.properties`: overrides para execucao conteinerizada
- variaveis de ambiente e propriedades Spring injetadas por `.env`, compose ou runtime

O profile `docker` e ativado por `SPRING_PROFILES_ACTIVE=docker`.

## Perfis relevantes

### Default

Usa `application.properties` e assume:

- Postgres local em `localhost:5432`
- Redis local em `127.0.0.1:6379`
- chave AES/JWT/pepper vindas do ambiente
- Vault desabilitado por default (`vault.enabled=false`)

### Docker

Usa `application-docker.properties` para:

- binding em `0.0.0.0:8080`
- resolver servicos por DNS Docker
- configurar pool Hikari maior
- ligar SQL init adicional
- parametrizar integracoes de custodia, lightning, onramp e monitors

### Producao

Nao existe um arquivo `application-prod.properties` visivel aqui, mas o codigo tem um gate de seguranca de producao em `source/config/production`.

Esse gate so entra quando o profile ativo contem `prod` ou `production`.

## Variaveis de ambiente de base

O arquivo `.env.example` documenta o baseline minimo para desenvolvimento:

### Banco e Redis

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `REDIS_PASSWORD`

### Segredos principais

- `AES_SECRET`
- `JWT_SECRET`
- `PASSWORD_PEPPER`
- `HMAC_SECRET_KEY`
- `FOUNDER_TOTP_SECRET`

### WebAuthn

- `WEBAUTHN_RP_ID`
- `WEBAUTHN_RP_NAME`
- `WEBAUTHN_ORIGINS`

### Bitcoin

- `BITCOIN_ESPLORA_BASE_URL`
- `BITCOIN_PLATFORM_MASTER_XPUB`
- `BITCOIN_HOT_WALLET_ADDRESS`
- `BITCOIN_HOT_WALLET_XPUB`
- `BITCOIN_HOT_WALLET_XPUB_SCAN_RANGE`

## Propriedades criticas por area

### HTTP, CORS e runtime web

- `server.address`
- `server.port`
- `app.cors.allowed-origins`
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`
- `server.tomcat.max-http-form-post-size`

Observacao:

- HTTP CORS exige origem explicita. Wildcard em `app.cors.allowed-origins` e rejeitado pelo codigo.
- Os endpoints WebSocket hoje usam `allowedOriginPatterns("*")` no registrador STOMP, entao o comportamento de origem nao e identico ao pipeline HTTP.

### Banco e persistencia

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.jpa.hibernate.ddl-auto`
- `spring.sql.init.mode`
- `spring.sql.init.schema-locations`
- `spring.jpa.defer-datasource-initialization`

No profile `docker`, o backend usa:

- `ddl-auto=update`
- `spring.sql.init.mode=always`
- `classpath:db/migration.sql`

### Redis

- `spring.data.redis.host`
- `spring.data.redis.port`
- `spring.data.redis.password`
- `spring.data.redis.timeout`

### Segredos e cripto

- `api.secret.aes.secret`
- `api.secret.token.secret`
- `api.secret.pepper.secret`

Em desenvolvimento, a chave AES pode vir de `AES_SECRET`. Em modo Vault, a chave mestra passa a ser provisionada dinamicamente.

### WebAuthn / Passkeys

- `webauthn.relying-party-id`
- `webauthn.relying-party-name`
- `webauthn.origins`
- `WEBAUTHN_RP_ID`
- `WEBAUTHN_RP_NAME`

Esses valores precisam corresponder exatamente ao host/origem vistos pelo cliente.

### Vault, attestation e soberania

- `vault.enabled`
- `vault.url`
- `vault.onion.file`
- `vault.proxy.host`
- `vault.proxy.port`
- `vault.proxy.path`
- `security.admin.attestation-token`
- `sovereignty.heartbeat.*`

Producao segura implica:

- `vault.enabled=true`
- `vault.proxy.path` configurado quando houver acesso onion via Tor
- `security.admin.attestation-token` definido para operacoes administrativas

### MPC e quorum

- `mpc.sidecar.host`
- `mpc.sidecar.port`
- `mpc.sidecar.tls.enabled`
- `mpc.sidecar.tls.cert-chain`
- `mpc.sidecar.tls.private-key`
- `mpc.sidecar.tls.trust-cert-collection`
- `quorum.shard.urls`
- `quorum.allow-local-simulation`

### Custodia, Lightning e onramp

- `custody.provider-name`
- `custody.base-url`
- `custody.api-key`
- `custody.mock-mode`
- `custody.*-path`
- `lightning.provider.base-url`
- `lightning.provider.api-key`
- `lightning.provider.*-path`
- `onramp.moonpay.api-key`
- `onramp.moonpay.secret-key`
- `onramp.moonpay.base-currency-code`
- `onramp.banxa.fiat-type`

### Bitcoin e transacoes

- `bitcoin.esplora.base-url`
- `bitcoin.deposit-address`
- `bitcoin.min-confirmations`
- `bitcoin.payment-link-expiration-minutes`
- `bitcoin.mock-mode`
- `transactions.external.fee-rate`
- `transactions.inbound-monitor.*`
- `transactions.deposit.mock-credit.*`
- `blockchain.monitor.interval.min`
- `blockchain.monitor.interval.max`

### Tesouraria e auditoria

- `financial.audit.wallet-xpub-gap-limit`
- `financial.audit.treasury-xpub-scan-range`
- `audit.merkle.interval-ms`
- `security.founder.totp-secret`
- `security.owner.hardware-signature`

## Guard-rails de producao

O codigo recusa subir em profile de producao se detectar combinacoes inseguras.

### Flags que nao podem estar ativas

- `bitcoin.mock-mode=true`
- `custody.mock-mode=true`
- `app.dev.inject-test-balance=true`
- `quorum.allow-local-simulation=true`

### Flags que precisam estar corretas

- `vault.enabled=true`
- `mpc.sidecar.tls.enabled=true`
- `app.cors.allowed-origins` sem wildcard
- `quorum.shard.urls` preenchido
- `custody.base-url` preenchido
- `custody.api-key` preenchido
- `lightning.provider.base-url` preenchido
- `lightning.provider.api-key` preenchido

### Beans mock/stub

O startup de producao tambem inspeciona beans e barra classes `mock`/`stub`, com excecoes pontuais explicitamente permitidas no codigo.

## Configuracoes operacionais importantes

### Logs

- `logging.level.root`
- `logging.level.source`
- `logging.level.org.springframework.web`
- `logging.level.org.springframework.security`

### Actuator

- `management.endpoints.web.exposure.include=health`
- `management.endpoint.health.show-details=always`

### Threads e pool

- `spring.threads.virtual.enabled=true`
- `spring.datasource.hikari.*`

## Observacoes praticas

- O arquivo `.env.example` nao cobre todas as propriedades usadas em `application-docker.properties`; ele documenta o baseline minimo.
- O profile `docker` contem defaults voltados a ambiente conteinerizado e simulacoes operacionais. Nao trate isso como especificacao final de producao sem validar os checks de `source/config/production`.
- O mecanismo de binding do Spring permite usar env vars em maiusculas para sobrescrever propriedades com `.` e `-`.

Exemplos:

- `SPRING_DATASOURCE_URL` -> `spring.datasource.url`
- `WEBAUTHN_RP_ID` -> `webauthn.relying-party-id`/props relacionadas
- `MPC_SIDECAR_HOST` -> `mpc.sidecar.host`

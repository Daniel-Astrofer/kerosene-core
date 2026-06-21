# Backend Architecture Cleanup Audit

Status: fase-6 read-only audit  
Scope: `backend/kerosene`, with notes for adjacent backend modules where they affect the same architecture rules  
Source rules: `docs/AGENTS/NIGHTLY_ORCHESTRATION_QUEUE.md` and `docs/backend/KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md`

## Executive Summary

The backend already contains several moves toward the target architecture, especially in KFE transaction submission, websocket handling, production safety checks, and some recovery chains. The main cleanup debt is uneven adoption: some flows use `controller -> application/usecase -> domain/ports -> infrastructure`, while other flows still place repositories, transactions, provider probes, status transitions, and exception mapping directly in controllers or broad services.

The highest-risk areas are authentication device/passkey flows, admin operations, KFE wallet/outbox execution, and global error/log/audit consistency. These areas touch security, money, critical infrastructure, or operator visibility, so the cleanup should be incremental, test-backed, and aligned to the design system instead of doing broad rewrites.

## P0: Boundary And Safety Issues

### 1. Controllers Still Contain Business Logic And Persistence

Design-system rule violated: controllers should receive HTTP, validate request shape, call a use case, and map the response. They should not call repositories directly, open business transactions, decide domain state, or catch exceptions to produce local error contracts.

Evidence:

- `source.auth.controller.DeviceKeyController` is 352 lines, injects `DeviceKeyCredentialRepository` and `UserRepository`, owns `@Transactional` methods, advances replay counters, persists credentials, revokes devices, finalizes signup, issues JWTs, checks account state, and handles several exception branches.
- `source.auth.controller.PasskeyController` injects `PasskeyCredentialRepository` and `UserRepository`, looks up authenticated users, mutates credential status, saves the repository, and builds auth errors inline.
- `source.common.admin.AdminOperationsController` calls KFE repositories directly, queries all transactions/outbox rows, calculates financial metrics, probes Bitcoin Core and Lightning providers, and shapes operational privacy boundaries in the controller.
- `source.kfe.controller.KfeTransactionController` catches database constraint exceptions and performs idempotency recovery in the controller, even though idempotency is an application concern.

Impact:

- Security and money behavior is split between HTTP adapters and application code, making invariants harder to test without Spring MVC.
- Controller-specific error handling bypasses the global error model and structured logging/audit expectations.
- Repository access from controllers makes future port extraction harder because HTTP code depends on persistence details.

Cleanup direction:

- Extract controller behavior into use cases such as `RegisterDeviceKeyUseCase`, `VerifyDeviceKeyLoginUseCase`, `RevokeDeviceKeyUseCase`, `UpdatePasskeyDeviceStatusUseCase`, `GetAdminOperationsOverviewUseCase`, and `RecoverKfeTransactionIdempotencyUseCase`.
- Keep controllers thin: parse auth context, validate request DTOs, call the use case, and return `ApiResponse`.
- Move `@Transactional` from controllers into application services/use cases.

### 2. Unsafe Or Inconsistent Error Handling

Design-system rule violated: external/provider/framework errors must be translated to safe internal errors; controllers must not return raw exception messages; critical flows must fail closed.

Evidence:

- `DeviceKeyController` catches broad `RuntimeException` and returns `badRequest` with a generic message while also doing account creation, credential persistence, and JWT issuance in the same flow.
- `AdminOperationsController` catches `RuntimeException` during provider probes and returns class names in the payload. This is safer than raw messages, but it is not routed through a shared sanitized diagnostic model.
- `GlobalExceptionHandler` sanitizes many `IllegalArgumentException` and `IllegalStateException` messages, but several handlers still return `ex.getMessage()` directly for auth, mining, and vault readiness paths.
- `KfeTransactionController` uses persistence exceptions as normal idempotency control flow at the HTTP layer.

Impact:

- Error contracts differ by controller, so clients receive different status/message/error-code patterns for similar failures.
- Security-sensitive auth and device-key failures can be hard to audit because some are local controller branches and some are global exceptions.
- Using database exceptions for idempotency recovery outside the use case obscures whether the conflict is expected, malicious, or a persistence fault.

Cleanup direction:

- Standardize domain/application exceptions with stable error codes and safe public messages.
- Move idempotency conflict handling into `KfeTransactionIdempotencyUseCase` or the submit use case.
- Reserve raw exception details for structured runtime logs with sanitized metadata, not public responses.

### 3. Critical Flows Lack Uniform Audit Events

Design-system rule violated: login, token renewal, step-up, wallet creation, address derivation, settlement, failure, outbox dispatch, Vault/MPC/LND calls, and signing flows are not complete without audit events.

Evidence:

- KFE has `KfeAuditLogService` and event recording in wallet and execution flows, but taxonomy is local strings such as `KFE_WALLET_CREATED`, `KFE_TRANSACTION_SETTLED`, `KFE_EXECUTION_RETRYABLE_FAILURE`, and `KFE_WALLET_ADDRESS_ROTATED`.
- Device-key/passkey login, credential registration, revocation, replay rejection, account activation, backup-code regeneration, JWT session revocation, and admin-access decisions are not consistently represented through a shared audit event port/taxonomy.
- `AdminOperationsController` exposes KFE audit logs, but admin operation access itself is not clearly audited as an operator action.

Impact:

- Incident response cannot reliably reconstruct auth/admin/KFE/Vault/MPC events from one stable event vocabulary.
- Some important denial/fail-closed decisions are only visible in runtime logs or HTTP responses.

Cleanup direction:

- Introduce an `AuditLogPort` with stable event names and sanitized payload rules.
- Start with the queue minimum taxonomy for auth, admin access, KFE, Vault, and MPC.
- Keep KFE hash-chain audit behavior, but route event naming through a shared taxonomy or adapter.

## P1: SOLID And Clean Architecture Drift

### 4. Broad Services Have Multiple Reasons To Change

Design-system rule violated: services should map to a business intention; large services should be broken by operation.

Evidence:

- `OperationalHealthService` is 857 lines and owns many dependency probes, health payload shaping, and operational status rules.
- `PasskeyService` is 651 lines and mixes challenge lifecycle, cryptographic verification helpers, metadata extraction, compatibility behavior, and low-level passkey parsing.
- `KfeWalletService` is 539 lines and handles wallet creation, uniqueness, quorum, MPC keygen, address issuance/rotation, dev balance injection, audit, dashboard publishing, and mapping.
- `KfeExecutionTransactionHelper` is 509 lines and handles outbox preparation, settlement, retry/final failure, reconciliation, balance movements, statements, idempotency updates, audit, JSON parsing, and backoff.
- `AdminAccessService` is 522 lines and appears to own login start/poll, key rotation, attempt decisions, device status changes, and event persistence.

Impact:

- SRP violations make changes risky because adding one business rule can affect unrelated behavior in the same class.
- Tests become broad and scenario-heavy instead of focused around one use case or policy.
- Naming such as `Helper` hides business ownership and can become a dumping ground.

Cleanup direction:

- Split by use case and policy, not by arbitrary helper classes.
- Example targets: `CreateKfeWalletUseCase`, `RotateKfeWalletAddressUseCase`, `IssueKfeReceiveAddressUseCase`, `PrepareKfeOutboxExecutionUseCase`, `SettleKfeOutboundUseCase`, `MarkKfeOutboxFailureUseCase`, `PasskeyChallengeService`, `PasskeyAssertionVerifier`, and `AdminAccessDecisionUseCase`.
- Keep shared pure policies in domain/application classes with narrow names.

### 5. Application Code Depends On Concrete Infrastructure

Design-system rule violated: application depends on ports; infrastructure implements ports. Use cases should not depend on concrete HTTP clients, Redis implementations, repositories, or provider clients when a domain port is appropriate.

Evidence:

- `AdminOperationsController` depends directly on `BitcoinCoreRpcClient`, `LightningClient`, and KFE repositories.
- KFE services commonly depend directly on Spring Data repositories and provider/service implementations instead of ports.
- Auth controllers and services depend on concrete repositories in controller code, while some newer code uses orchestrators and chain handlers.
- `AddressDerivationService` is under `source.common.service`, uses infrastructure libraries, and is consumed by KFE wallet business logic. The current placement blurs whether it is a domain policy, application service, or infrastructure adapter.

Impact:

- Provider swaps and local-dev adapters require changes in application/controller code.
- Unit tests require more Spring or repository mocking than necessary.
- Business rules are harder to reason about independently from persistence and provider behavior.

Cleanup direction:

- Introduce ports at the operation boundary before moving files: `DeviceKeyCredentialPort`, `PasskeyCredentialPort`, `AdminOperationsReadPort`, `BlockchainProbePort`, `LightningProbePort`, `WalletAddressDerivationPort`, `KfeAuditPort`, and `KfeOutboxPort`.
- Use infrastructure adapters to wrap Spring Data repositories and provider clients.
- Avoid moving packages until ports and tests prove the boundary.

### 6. Naming Is Not Yet A Shared Domain Language

Design-system rule violated: names must describe business intention; generic `Service`, `Helper`, `Manager`, `Util`, and mixed language names should be avoided for new/refactored code.

Evidence:

- Generic names: `KfeExecutionTransactionHelper`, `CryptoUtils`, `TickerService`, `OperationalHealthService`, `MobileDownloadService`.
- Mixed language remains in interfaces/methods such as `JwtServicer`, `RedisServicer`, and `buscarPorId`.
- KFE uses both precise application names (`KfeSubmitTransactionUseCase`, `KfeTransactionStateMachine`) and broad service names (`KfeWalletService`, `KfePaymentRequestService`, `KfeWalletNetworkService`).
- Public errors and comments mix English and Portuguese, while API contracts use English DTO names.

Impact:

- Agents and maintainers cannot infer ownership from class names.
- Mixed naming increases duplicated abstractions because developers do not know which term is canonical.

Cleanup direction:

- Adopt English technical/domain names for backend code and keep localized copy at the response/i18n boundary.
- Replace `Servicer` with `Port` or `Client` based on responsibility.
- Rename `Helper` only when extracting actual use cases/policies, not as cosmetic churn.

## P2: Documentation, Logging, And Observability Gaps

### 7. Code Documentation Is Uneven Around Invariants

Design-system and queue rule affected: document intent, invariants, risks, side effects, security decisions, idempotency, fail-closed behavior, and external calls.

Evidence:

- Some classes have helpful intent comments, but critical methods such as device-key verification, wallet activation, address rotation, outbox settlement, replay rejection, and admin metrics do not consistently document invariants or side effects.
- Broad services contain many private branches where the business meaning is not obvious without reading the entire method.
- Existing docs cover APIs and architecture, but there is no per-flow note that maps auth/KFE/Vault/MPC use cases to owning class, audit event, transaction boundary, and external dependency.

Impact:

- Future agents may preserve syntax but break security or money invariants.
- Operational decisions such as provider fallback, fail-closed behavior, and reconciliation require code archaeology.

Cleanup direction:

- Add a backend code documentation standard to the design system before large refactors.
- Require short Javadoc or comments on public controllers/use cases/ports and on methods with security, audit, idempotency, external calls, or fail-closed behavior.
- Avoid comments on trivial getters, setters, mappers, or obvious assignments.

### 8. Runtime Logging Is Present But Not Structured Enough

Design-system rule violated: observability requires structured logs, correlation, and safe metadata for critical flows.

Evidence:

- `LogContextFilter`, `LoggingFilter`, `LogDomain`, and `LogSanitizer` exist, but many logs remain free-form strings such as `[KFE Wallet] ...`, `[Ticker] ...`, and `[GlobalExceptionHandler] ...`.
- Several critical logs include only exception messages, without stable fields for `event`, `domain`, `operation`, `traceId`, `errorCode`, `exceptionType`, and sanitized identifiers.
- Controller-local error branches often return directly without a corresponding structured log or audit event.

Impact:

- Production diagnosis depends on text search instead of stable log fields.
- Correlating auth, KFE, Vault, MPC, provider, and outbox events is harder than necessary.

Cleanup direction:

- Define a small structured logging contract and helper before migrating individual flows.
- Require sanitized metadata only; no secrets, raw credentials, mnemonics, xpubs where avoidable, invoices, tokens, or raw provider payloads.
- Start with auth login/device-key, KFE transaction submit/outbox, wallet creation/address issuance, provider probes, Vault/MPC calls, and global exception handling.

### 9. Admin And Operational Read Models Are Too Close To Controllers

Design-system rule affected: controllers should not calculate business/operational read models or call repositories directly.

Evidence:

- `AdminOperationsController.metrics()` loads all KFE transactions and outbox items, groups them, calculates BTC amounts, and constructs privacy-bound response payloads.
- `AdminOperationsController.blockchain()` and `lightning()` probe providers and decide `UP`/`DEGRADED`/`DOWN` status directly.

Impact:

- Admin responses may become slow as data grows because the controller reads all rows and aggregates in memory.
- Privacy rules live in HTTP code rather than a named admin read model.
- Provider health semantics are duplicated risk if another operations surface is added.

Cleanup direction:

- Extract read models such as `AdminKfeMetricsReadModel`, `BlockchainOperationsProbe`, and `LightningOperationsProbe`.
- Push aggregation to repository queries where possible.
- Add audit events for admin operational access if the output can reveal sensitive platform state.

## Positive Patterns To Preserve

- KFE transaction submission already exposes `FinancialApi` and use-case classes, which is closer to the target boundary than older controllers.
- `KfeTransactionStateMachine`, `KfeTransactionRequestValidator`, and idempotency/outbox classes show useful operation-oriented extraction.
- Websocket inbound handling uses a chain of smaller handlers, a better fit for open/closed growth than one large interceptor.
- Production safety checks are split into focused classes and should be used as a model for future backend cleanup.
- KFE audit hashing/redaction patterns should be preserved while standardizing taxonomy and ports.

## Suggested Cleanup Order

1. Define backend documentation, structured logging, and audit event standards before broad code movement.
2. Extract auth device/passkey controller logic into use cases because it has the clearest controller boundary violations and security impact.
3. Extract admin operations probes/read models because the current controller mixes provider calls, repository reads, aggregation, and privacy shaping.
4. Split KFE wallet and outbox execution services by use case, preserving existing tests and audit behavior.
5. Introduce ports around repositories/provider clients when touching each flow; avoid repo-wide package moves without tests.

## Validation Checklist For Future Refactors

- Controller has no repository dependency.
- Controller has no `@Transactional`.
- Controller does not catch broad exceptions to build business responses.
- Use case name describes one business intention.
- Critical use case has structured runtime logs and audit events.
- Public error message is safe and stable.
- External/provider error is translated at the adapter/application boundary.
- Idempotency conflict handling is inside application code.
- Tests cover success, fail-closed rejection, unsafe-provider failure, duplicate/idempotent replay, audit emission, and sanitized error response where applicable.

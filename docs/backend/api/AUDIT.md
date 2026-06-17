# Auditoria API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `11`.

Controllers cobertos:

- `LedgerAuditController`
- `MerkleAuditController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/audit/history` | `MerkleAuditController.history` | AUTHENTICATED/METHOD_SECURITY<br>`@PreAuthorize("isAuthenticated()")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | query: limit: int | `ResponseEntity<?>` | [MerkleAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java#L64) |
| `GET` | `/audit/latest-root` | `MerkleAuditController.latestRoot` | AUTHENTICATED/METHOD_SECURITY<br>`@PreAuthorize("isAuthenticated()")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<?>` | [MerkleAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java#L45) |
| `POST` | `/audit/trigger` | `MerkleAuditController.triggerAudit` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<?>` | [MerkleAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/audit/MerkleAuditController.java#L79) |
| `GET` | `/v1/audit/config` | `LedgerAuditController.getTreasuryAuditConfig` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<?>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L117) |
| `PUT` | `/v1/audit/config` | `LedgerAuditController.updateTreasuryAuditConfig` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: TreasuryAuditConfigRequestDTO | `ResponseEntity<?>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L129) |
| `POST` | `/v1/audit/reserves/operational-proof` | `LedgerAuditController.generateOperationalReserveProof` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<OperationalReserveProofResponseDTO>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L144) |
| `POST` | `/v1/audit/siphon` | `LedgerAuditController.siphonFees` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: Map<String, String> | `ResponseEntity<Map<String, String>>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L155) |
| `POST` | `/v1/audit/siphon/requests` | `LedgerAuditController.requestSiphonPayout` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | body: Map<String, String> (optional) | `ResponseEntity<?>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L186) |
| `POST` | `/v1/audit/siphon/requests/{requestId}/approve` | `LedgerAuditController.approveSiphonPayout` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: requestId: UUID<br>body: Map<String, String> (optional) | `ResponseEntity<?>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L200) |
| `POST` | `/v1/audit/siphon/requests/{requestId}/cancel` | `LedgerAuditController.cancelSiphonPayout` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | path: requestId: UUID<br>body: Map<String, String> (optional) | `ResponseEntity<?>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L222) |
| `GET` | `/v1/audit/stats` | `LedgerAuditController.getTransparencyStats` | AUTHENTICATED<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<Map<String, Object>>` | [LedgerAuditController.java](../../../backend/kerosene/src/main/java/source/ledger/controller/LedgerAuditController.java#L82) |

## DTOs e Payloads

### `OperationalReserveProofResponseDTO`

Fonte: [OperationalReserveProofResponseDTO.java](../../../backend/kerosene/src/main/java/source/treasury/dto/OperationalReserveProofResponseDTO.java)

Campos observados no DTO:

- `Instant generatedAt`
- `String status`
- `boolean solvent`
- `boolean providersHealthy`
- `Assets assets`
- `Liabilities liabilities`
- `ChainState chainState`
- `MerkleProof merkleProof`
- `List<ProviderHealth> providers`
- `String snapshotHash`
- `String panicReason`

### `TreasuryAuditConfigRequestDTO`

Fonte: [TreasuryAuditConfigRequestDTO.java](../../../backend/kerosene/src/main/java/source/ledger/dto/TreasuryAuditConfigRequestDTO.java)

Campos observados no DTO:

- `BigDecimal maxWithdrawLimit`
- `String auditXpub`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.

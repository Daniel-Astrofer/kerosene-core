# Mining API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `5`.

Controllers cobertos:

- `MiningController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/mining/allocations` | `MiningController.listAllocations` | AUTHENTICATED | none | `ApiResponse<List<MiningAllocationResponseDTO>>` | [MiningController.java](../../../backend/kerosene/src/main/java/source/mining/controller/MiningController.java#L48) |
| `POST` | `/mining/allocations` | `MiningController.createAllocation` | AUTHENTICATED | body: MiningAllocationRequestDTO | `ApiResponse<MiningAllocationResponseDTO>` | [MiningController.java](../../../backend/kerosene/src/main/java/source/mining/controller/MiningController.java#L38) |
| `GET` | `/mining/allocations/{allocationId}` | `MiningController.getAllocation` | AUTHENTICATED | path: allocationId: UUID | `ApiResponse<MiningAllocationResponseDTO>` | [MiningController.java](../../../backend/kerosene/src/main/java/source/mining/controller/MiningController.java#L55) |
| `POST` | `/mining/allocations/{allocationId}/cancel` | `MiningController.cancelAllocation` | AUTHENTICATED | path: allocationId: UUID | `ApiResponse<MiningAllocationResponseDTO>` | [MiningController.java](../../../backend/kerosene/src/main/java/source/mining/controller/MiningController.java#L64) |
| `GET` | `/mining/rigs` | `MiningController.listRigOffers` | AUTHENTICATED | none | `ApiResponse<List<MiningRigOfferDTO>>` | [MiningController.java](../../../backend/kerosene/src/main/java/source/mining/controller/MiningController.java#L32) |

## DTOs e Payloads

### `MiningAllocationRequestDTO`

Fonte: [MiningAllocationRequestDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningAllocationRequestDTO.java)

Campos observados no DTO:

- `String walletName`
- `Long rigId`
- `BigDecimal requestedHashrate`
- `BigDecimal budgetBtc`
- `Integer durationHours`
- `String payoutAddress`
- `String poolUrl`
- `String workerName`
- `String totpCode`
- `String passkeyAssertionResponseJSON`
- `String confirmationPassphrase`

### `MiningAllocationResponseDTO`

Fonte: [MiningAllocationResponseDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningAllocationResponseDTO.java)

Campos observados no DTO:

- `UUID id`
- `Long rigId`
- `String rigName`
- `String walletName`
- `String algorithm`
- `BigDecimal allocatedHashrate`
- `String hashUnit`
- `Integer durationHours`
- `BigDecimal rentalCostBtc`
- `BigDecimal projectedGrossYieldBtc`
- `BigDecimal projectedNetYieldBtc`
- `BigDecimal refundedAmountBtc`
- `String status`
- `String providerRentalReference`
- `String payoutAddress`
- `String poolUrl`
- `String workerName`
- `LocalDateTime startsAt`
- `LocalDateTime endsAt`
- `LocalDateTime settledAt`

### `MiningRigOfferDTO`

Fonte: [MiningRigOfferDTO.java](../../../backend/kerosene/src/main/java/source/mining/dto/MiningRigOfferDTO.java)

Campos observados no DTO:

- `Long id`
- `String rigCode`
- `String displayName`
- `String algorithm`
- `String hashUnit`
- `BigDecimal availableHashrate`
- `BigDecimal pricePerUnitDayBtc`
- `BigDecimal projectedBtcYieldPerUnitDay`
- `Integer minRentalHours`
- `Integer maxRentalHours`
- `String provider`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.

# Treasury API

Fonte principal: controllers, DTOs e configuracao de seguranca em `backend/kerosene/src/main/java/source/**`.

`docs/backend/API_REFERENCE.md` permanece como referencia consolidada e foi usado apenas como auditoria de cobertura. A politica efetiva vem de `EndpointPolicyRegistry`, `Security` e de anotacoes `@PreAuthorize`.


## Escopo

Endpoints neste arquivo: `1`.

Controllers cobertos:

- `TreasuryController`

## Endpoints

| Metodo | Path | Controller.handler | Auth | Request | Response | Fonte |
| --- | --- | --- | --- | --- | --- | --- |
| `GET` | `/treasury/overview` | `TreasuryController.overview` | ADMIN/METHOD_SECURITY<br>`@PreAuthorize("hasRole('ADMIN')")`<br>cond: `@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")` | none | `ResponseEntity<TreasuryOverviewDTO>` | [TreasuryController.java](../../../backend/kerosene/src/main/java/source/treasury/controller/TreasuryController.java#L32) |

## DTOs e Payloads

### `TreasuryOverviewDTO`

Fonte: [TreasuryOverviewDTO.java](../../../backend/kerosene/src/main/java/source/treasury/dto/TreasuryOverviewDTO.java)

Campos observados no DTO:

- `BigDecimal totalOnchainBtc`
- `BigDecimal lightningNodeBtc`
- `BigDecimal inboundLiquidityBtc`
- `BigDecimal outboundLiquidityBtc`
- `BigDecimal reservedOnchainBtc`
- `BigDecimal reservedLightningBtc`
- `BigDecimal availableOnchainBtc`
- `BigDecimal availableLightningBtc`
- `boolean lightningSendsAllowed`
- `String liquidityState`


## Notas de Seguranca

- Rotas sem politica declarada sao negadas por `anyRequest().denyAll()` em `Security`.
- Regras por `@PreAuthorize` prevalecem como seguranca em nivel de metodo.
- Bodies mutantes seguem os filtros globais de content-type, tamanho de payload e `Digest` quando enviado.

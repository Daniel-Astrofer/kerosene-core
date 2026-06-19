# Treasury API — removida

A API financeira legada de Treasury foi expurgada. O pacote `source.treasury` não existe mais e nenhuma rota `REMOVED_LEGACY_FINANCIAL_ROUTE` deve ser restaurada.

## Substituto KFE

Use exclusivamente:

```http
GET /api/admin/kfe/reserves/overview
```

Implementação ativa:

- Controller: `source.kfe.controller.KfeReserveAdminController`
- Service: `source.kfe.service.KfeReserveOverviewService`
- DTO: `source.kfe.dto.KfeReserveOverviewResponse`

## Contrato

```json
{
  "success": true,
  "message": "KFE reserve overview retrieved.",
  "data": {
    "totalOnchainBtc": 0.0,
    "lightningNodeBtc": 0.0,
    "inboundLiquidityBtc": 0.0,
    "outboundLiquidityBtc": 0.0,
    "reservedOnchainBtc": 0.0,
    "reservedLightningBtc": 0.0,
    "availableOnchainBtc": 0.0,
    "availableLightningBtc": 0.0,
    "lightningSendsAllowed": true,
    "liquidityState": "HEALTHY"
  }
}
```

## Regra arquitetural

Qualquer visão administrativa de reservas, liquidez, payout ou reconciliação financeira deve nascer em `source.kfe` ou sob `/api/admin/kfe/**`.

Não reintroduzir:

```text
source.treasury
/treasury/**
TreasuryOverviewDTO
```

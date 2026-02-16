# 🏗️ Arquitetura Redis Payment Links

## Diagrama de Fluxo

```
┌─────────────────────────────────────────────────────────────────┐
│                      REST API Client                             │
│                  (Mobile App / Web)                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    PaymentLinkController                         │
│  Endpoints:                                                       │
│  • POST   /api/payment-links              [Create]              │
│  • GET    /api/payment-links/{linkId}     [Get]                │
│  • POST   /api/payment-links/{linkId}/confirm  [Confirm]       │
│  • POST   /api/payment-links/{linkId}/complete [Complete]      │
│  • GET    /api/payment-links/user/{userId}     [List User]     │
│  • DELETE /api/payment-links/{linkId}/cache    [Clear Cache]   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   PaymentLinkService                             │
│                                                                   │
│  • createPaymentLink()      → Cria no DB + Redis                │
│  • getPaymentLink()         → Redis (1st) ou DB (fallback)      │
│  • confirmPayment()         → Valida + Sincroniza               │
│  • completePayment()        → Libera + Atualiza                 │
│  • removeFromRedis()        → Limpeza manual                     │
│  • updateRedisStatus()      → Sincronização                      │
│                                                                   │
└────┬──────────────────────┬──────────────────────┬──────────────┘
     │                      │                      │
     ▼                      ▼                      ▼
┌──────────────┐   ┌───────────────────┐  ┌────────────────┐
│  PostgreSQL  │   │   Redis Cache     │  │  BlockchainInfo│
│              │   │   (TTL: 3 horas)  │  │   API          │
│ PaymentLink  │   │                   │  │                │
│ Entity       │   │ payment_link:xxx  │  │ Validate TXID  │
│              │   │                   │  │                │
│ Fields:      │   │ (JSON Serialized) │  │                │
│ • id         │   │                   │  │                │
│ • userId     │   └───────────────────┘  └────────────────┘
│ • amountBtc  │
│ • status     │
│ • expiresAt  │
│ • txid       │
│ • createdAt  │
│ • paidAt     │
│ • completedAt│
│              │
└──────────────┘

```

## Fluxo de Dados

### 1️⃣ **Criar Payment Link**
```
Cliente HTTP
    ↓
POST /api/payment-links
{
  "userId": 1,
  "amountBtc": 0.5,
  "description": "Depósito"
}
    ↓
PaymentLinkController
    ↓
PaymentLinkService.createPaymentLink()
    ├─ Gera ID único (pay_xxx)
    ├─ Cria PaymentLinkEntity
    ├─ Salva em PostgreSQL
    ├─ Converte para DTO
    ├─ Armazena no Redis (TTL = 3 horas)
    └─ Retorna DTO para cliente
    
Resultado no Redis:
Key: payment_link:pay_xxx
Value: {
  "id": "pay_xxx",
  "userId": 1,
  "amountBtc": 0.5,
  "status": "pending",
  "expiresAt": "2024-12-25T15:00:00",
  ...
}
TTL: 10800 segundos (3 horas)
```

### 2️⃣ **Consultar Payment Link (Cache-First)**
```
Cliente HTTP
    ↓
GET /api/payment-links/pay_xxx
    ↓
PaymentLinkController
    ↓
PaymentLinkService.getPaymentLink()
    ├─ Busca no Redis (FAST)
    │   ├─ Se encontrado → Valida expiração → Retorna
    │   └─ Se não encontrado ↓
    │
    ├─ Busca em PostgreSQL (FALLBACK)
    │   ├─ Se encontrado
    │   ├─ Valida expiração
    │   ├─ Re-adiciona ao Redis
    │   └─ Retorna DTO
    │
    └─ Se não encontrado em nenhum → Retorna NULL

Exemplo:
• 1ª requisição: Redis miss + DB hit (~100ms)
• 2ª-1000ª requisição: Redis hit (~5ms cada)
```

### 3️⃣ **Confirmar Pagamento**
```
Cliente HTTP
    ↓
POST /api/payment-links/pay_xxx/confirm
{
  "txid": "abc123...",
  "fromAddress": "1A1z7..."
}
    ↓
PaymentLinkService.confirmPayment()
    ├─ Busca no DB
    ├─ Valida status (pendente)
    ├─ Valida expiração
    ├─ Valida TXID na BlockchainInfo
    ├─ Atualiza status para "paid" em DB
    ├─ Atualiza status para "paid" em Redis
    └─ Retorna DTO atualizado

Resultado:
DB: payment_link.status = "paid"
Redis: valor atualizado (TTL reset para 3 horas)
```

### 4️⃣ **Completar/Liberar Pagamento**
```
Cliente HTTP
    ↓
POST /api/payment-links/pay_xxx/complete
    ↓
PaymentLinkService.completePayment()
    ├─ Busca no DB
    ├─ Valida status (paid)
    ├─ Atualiza status para "completed"
    ├─ Define completedAt = agora
    ├─ Salva em DB
    ├─ Atualiza Redis
    └─ Retorna DTO final

Resultado:
Status: "pending" → "paid" → "completed"
Valor: Liberado para saque
```

## Sincronização DB ↔️ Redis

```
┌────────────────────────────────────────────────────┐
│              Evento               │   DB   │ Redis │
├────────────────────────────────────────────────────┤
│ Criar Link                        │   ✓    │   ✓   │
│ Consultar Link                    │   ✓    │   ✓   │
│ Confirmar Pagamento               │   ✓    │   ✓   │
│ Completar Pagamento               │   ✓    │   ✓   │
│ TTL Expiração (3 horas)           │   -    │  Auto │
│ Remover Manual do Cache           │   -    │   ✓   │
└────────────────────────────────────────────────────┘

Estratégia: Write-Through
• Sempre escreve no DB primeiro
• Depois sincroniza com Redis
• Garante consistência de dados
```

## Estrutura de Dados no Redis

```
┌─────────────────────────────────────────────────┐
│            Redis Hash Serializado               │
├─────────────────────────────────────────────────┤
│                                                 │
│ Key: "payment_link:pay_a1b2c3d4e5f6"           │
│                                                 │
│ Value: (JSON String)                           │
│ {                                              │
│   "id": "pay_a1b2c3d4e5f6",                   │
│   "userId": 1,                                 │
│   "amountBtc": 0.5,                           │
│   "description": "Depósito",                   │
│   "depositAddress": "1A1z7...",                │
│   "status": "pending|paid|completed|expired", │
│   "txid": null ou "abc123...",                │
│   "expiresAt": "2024-12-25T15:00:00",        │
│   "createdAt": "2024-12-25T12:00:00",        │
│   "paidAt": null,                             │
│   "completedAt": null                         │
│ }                                              │
│                                                 │
│ TTL: 10800 segundos (3 horas)                 │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Performance Comparison

```
┌──────────────────────┬─────────────┬──────────────┐
│ Operação             │ Sem Redis   │ Com Redis    │
├──────────────────────┼─────────────┼──────────────┤
│ 1ª Leitura           │ 100ms (DB)  │ 100ms (DB)   │
│ 2ª-100ª Leitura      │ 100ms (DB)  │ 5ms (Redis)  │
│ Média (100 req)      │ 100ms       │ 6.95ms       │
│ Média (1000 req)     │ 100ms       │ 5.495ms      │
│ Cache Hit Rate       │ 0%          │ 99%          │
│                      │             │              │
│ 1000 requisições     │ 100seg      │ 5.5seg       │
│ Melhoria             │ -           │ 95% ↑        │
└──────────────────────┴─────────────┴──────────────┘
```

## Cenários de Uso

### 📱 Mobile App
```
[App] → GET /api/payment-links/{linkId}
         ↓
      [Redis] HIT → (5ms)
         ↓
      [Retorna info rápido ao app]

Resultado: UX fluida, sem delay
```

### 🔄 Webhook do Blockchain
```
[Blockchain] → POST /api/payment-links/{linkId}/confirm
                ↓
             [Valida + Confirma]
                ↓
             [DB + Redis updated]
                ↓
             [Log: ✅ Pagamento confirmado]

Resultado: Síncrono, atualização imediata
```

### 📊 Dashboard Admin
```
[Admin] → GET /api/payment-links/user/{userId}
           ↓
        [Busca no DB] ← Redis não cobre lista
           ↓
        [Retorna lista completa]

Resultado: Dados frescos, sempre do DB
```

## Tratamento de Falhas

```
┌─────────────────────────────────────────────────┐
│           Cenário de Falha                      │
├─────────────────────────────────────────────────┤
│                                                 │
│ Redis DOWN:                                    │
│ • Fallback automático para DB                  │
│ • Sem impacto para usuário                     │
│ • Performance degrada a 100ms (normal)         │
│                                                 │
│ DB DOWN:                                       │
│ • Redis retorna dados em cache                 │
│ • Alterações fila em memória (não implementado)│
│ • Sincronização quando DB voltar               │
│                                                 │
│ Ambos DOWN:                                    │
│ • Erro 500 (não há caminho de fallback)       │
│ • Alertar admin                                │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Monitoramento

```bash
# Comandos Redis CLI úteis:

# Ver memória usada
INFO memory

# Ver keys pattern
KEYS payment_link:*

# Contar links pendentes
KEYS payment_link:* | wc -l

# Ver detalhe de uma key
GET payment_link:pay_xxx

# Ver TTL restante
TTL payment_link:pay_xxx

# Limpar tudo
FLUSHDB

# Estatísticas
INFO stats
```

## Diagrama de Estados

```
                  CREATE
                    ↓
            ┌───────────────┐
            │    PENDING    │ ← Aguardando pagamento
            └───────┬───────┘
                    │
         ┌──────────┴──────────┐
         │                     │
    CONFIRM                  EXPIRE
         │                     │
         ▼                     ▼
    ┌────────┐          ┌─────────────┐
    │  PAID  │          │   EXPIRED   │ ← TTL expirou
    └───┬────┘          └─────────────┘
        │
    COMPLETE
        │
        ▼
    ┌──────────┐
    │COMPLETED │ ← Valor liberado
    └──────────┘
```

---

## 📚 Referências

- [Redis Documentation](https://redis.io/docs/)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)
- [Jackson JSON](https://github.com/FasterXML/jackson)
- [PostgreSQL JSON Support](https://www.postgresql.org/docs/current/datatype-json.html)

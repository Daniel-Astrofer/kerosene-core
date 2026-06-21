# Kerosene Backend Engineering Design System

Status: baseline obrigatório para novas refatorações de backend  
Escopo: `backend/kerosene`, `backend/mpc-sidecar`, `backend/vault`, `backend/adapters`, `backend/kerosene-infrastructure`  
Objetivo: tornar o backend coeso, previsível, auditável e seguro antes de ampliar refatorações com agentes.

---

## 0. Regra de aplicação obrigatória

Qualquer implementação feita por agentes no backend deve seguir este Design System.

Esta regra é obrigatória para alterações em código de produção, testes, migrations, configuração e adapters. O agente não deve tratar este documento como sugestão.

Se a tarefa recebida, o código legado ou uma instrução local conflitarem com este Design System, o agente deve:

1. não improvisar outro padrão;
2. reportar o conflito;
3. indicar o arquivo e a regra afetada;
4. aguardar novo escopo ou instrução explícita.

Nenhum agente deve alterar arquivos fora do escopo preciso recebido apenas para tentar adequar o projeto inteiro ao Design System. A adoção deve ser incremental e controlada.

---

## 1. Por que este documento existe

Este documento é o equivalente backend de um design system visual.

Em vez de botões, cores e espaçamentos, ele define:

- camadas oficiais;
- responsabilidades por tipo de classe;
- linguagem comum de domínio;
- modelo de erros;
- logs estruturados;
- regras de transação;
- idempotência;
- segurança;
- testes mínimos;
- contrato para agentes de alteração.

Todo código novo ou refatorado deve seguir este padrão, mesmo que o código legado ainda não siga.

---

## 2. Princípios de engenharia

### 2.1 Coesão antes de criatividade local

Cada módulo deve resolver problemas de forma semelhante.

Não é aceitável que autenticação, KFE, Vault, MPC e adapters tenham estilos incompatíveis para:

- erros;
- logs;
- validação;
- services;
- transações;
- nomes;
- resposta HTTP;
- retry;
- idempotência.

A solução correta é a mais consistente com o sistema, não a mais criativa no arquivo isolado.

### 2.2 Regra de negócio explícita

Regra de negócio deve ser visível em classes de application/domain, não escondida em:

- controller;
- repository;
- client HTTP;
- migration;
- listener;
- scheduler;
- exception handler.

### 2.3 Falhar fechado

Quando houver dúvida em segurança, dinheiro, assinatura, sessão ou infraestrutura crítica, o sistema deve falhar fechado.

Exemplos:

- token inválido não deve virar sessão parcial;
- config ausente não deve assumir default inseguro;
- provider externo falhou não deve marcar operação como concluída;
- outbox incerta não deve executar efeito externo duplicado sem idempotência;
- MPC/Vault indisponível não deve cair para signer local silencioso.

### 2.4 Observabilidade é parte do contrato

Fluxos críticos não estão completos sem log estruturado, correlação e evento auditável.

Fluxos críticos:

- login;
- renovação de token;
- step-up;
- criação de carteira;
- derivação de endereço;
- criação de invoice/payment request;
- reserva de ledger;
- settlement;
- failure;
- outbox dispatch;
- assinatura PSBT;
- chamada Vault/MPC/LND.

---

## 3. Arquitetura alvo

A arquitetura alvo deve ser organizada em camadas conceituais.

```text
api/controller
  -> application/usecase
    -> domain
    -> ports
      -> infrastructure/adapters
```

### 3.1 API / Controller

Responsabilidade:

- receber HTTP;
- autenticar contexto quando aplicável;
- validar formato superficial da request;
- converter DTO para command/query;
- chamar um use case;
- converter resultado para response;
- não conter regra de negócio.

Pode fazer:

- ler path/query/body;
- chamar validator de input;
- retornar status HTTP;
- mapear response DTO.

Não pode fazer:

- calcular saldo;
- calcular taxa;
- decidir status de transação;
- chamar repository diretamente;
- chamar Vault/LND/MPC diretamente;
- capturar exception e devolver `e.getMessage()`;
- iniciar transação manual para fluxo de negócio.

### 3.2 Application / Use Case

Responsabilidade:

- executar caso de uso;
- coordenar domínio, repositories e ports;
- controlar transação quando necessário;
- aplicar idempotência;
- publicar outbox;
- emitir logs de negócio.

Nomes recomendados:

```text
CreatePaymentRequestUseCase
ReserveWithdrawalUseCase
SettleLedgerTransactionUseCase
RefreshSessionUseCase
RotateVaultKeyUseCase
DeriveWalletAddressUseCase
```

Regra:

- um use case deve representar uma intenção de negócio;
- nomes genéricos como `Manager`, `Helper`, `Util` devem ser evitados;
- services grandes devem ser quebrados por operação.

### 3.3 Domain

Responsabilidade:

- invariantes;
- value objects;
- regras puras;
- estados válidos;
- transições permitidas;
- políticas de negócio.

Não deve depender de:

- Spring;
- HTTP;
- banco;
- Redis;
- Vault;
- LND;
- MPC;
- detalhes de framework.

Exemplos de domain/value objects:

```text
UserId
WalletId
AccountId
TransactionId
IdempotencyKey
SatoshiAmount
FeeAmount
LedgerEntry
LedgerBalance
PaymentRequestStatus
WithdrawalStatus
SessionId
TraceId
```

### 3.4 Ports

Ports são contratos que a regra de negócio usa.

Exemplos:

```text
LedgerRepositoryPort
IdempotencyRepositoryPort
OutboxPort
VaultClientPort
MpcSignerPort
LndInvoicePort
ClockPort
AuditLogPort
```

Regra:

- application depende de ports;
- infrastructure implementa ports;
- use case não deve depender de client HTTP concreto.

### 3.5 Infrastructure / Adapters

Responsabilidade:

- implementar ports;
- falar com banco;
- falar com Redis;
- falar com Vault;
- falar com MPC;
- falar com LND;
- publicar mensagens;
- traduzir erro externo em erro interno padronizado.

Não pode:

- decidir regra financeira;
- esconder fallback perigoso;
- retornar erro cru de provider ao controller;
- engolir exceção sem log estruturado.

---

## 4. SOLID aplicado ao Kerosene

### 4.1 Single Responsibility Principle

Uma classe deve ter um motivo principal para mudar.

Sinais de violação:

- classe com mais de uma família de métodos;
- service que valida request, calcula regra, chama provider e salva banco;
- controller com lógica condicional complexa;
- repository montando regra de negócio;
- client externo decidindo status interno.

Refatoração esperada:

```text
Antes:
PaymentService

Depois:
CreatePaymentRequestUseCase
PaymentRequestPolicy
LedgerReservationService
PaymentRequestRepository
OutboxPublisher
LndInvoiceGateway
```

### 4.2 Open/Closed Principle

Novos tipos de operação devem ser adicionados com novas políticas ou handlers, não com `if/else` crescente.

Preferir:

```text
LedgerOperationPolicy
DepositPolicy
WithdrawalPolicy
FeePolicy
InternalTransferPolicy
```

Evitar:

```text
if operation == DEPOSIT
if operation == WITHDRAWAL
if operation == FEE
if operation == REFUND
```

### 4.3 Liskov Substitution Principle

Implementações de ports devem respeitar o mesmo contrato.

Se `MpcSignerPort` promete assinatura distribuída, uma implementação com chave local única não pode se apresentar como MPC real sem deixar isso explícito no nome e no modo operacional.

Exemplo:

```text
MpcSignerPort
LocalDevSignerAdapter
ThresholdMpcSignerAdapter
```

`LocalDevSignerAdapter` só pode ser habilitado em ambiente dev/test.

### 4.4 Interface Segregation Principle

Interfaces pequenas e específicas.

Evitar:

```text
WalletService
  createWallet
  getBalance
  deriveAddress
  signPsbt
  syncLnd
  rotateKey
  validatePasskey
```

Preferir:

```text
WalletProvisioningPort
WalletBalanceReader
AddressDerivationPort
PsbtSigningPort
KeyRotationPort
PasskeyValidationPort
```

### 4.5 Dependency Inversion Principle

Casos de uso dependem de abstrações estáveis.

Correto:

```text
CreateWithdrawalUseCase -> LedgerRepositoryPort
CreateWithdrawalUseCase -> IdempotencyRepositoryPort
CreateWithdrawalUseCase -> OutboxPort
CreateWithdrawalUseCase -> FeePolicy
```

Incorreto:

```text
CreateWithdrawalUseCase -> JdbcTemplate
CreateWithdrawalUseCase -> LndHttpClient
CreateWithdrawalUseCase -> VaultRestTemplate
```

---

## 5. Convenções de nomes

### 5.1 Classes de entrada HTTP

```text
CreateWithdrawalRequest
CreateWithdrawalResponse
PaymentRequestController
AdminSessionController
```

### 5.2 Commands e Queries

Commands mudam estado.

```text
CreateWithdrawalCommand
RefreshSessionCommand
RotateVaultKeyCommand
```

Queries não mudam estado.

```text
GetWalletBalanceQuery
ListLedgerEntriesQuery
GetSessionStatusQuery
```

### 5.3 Use cases

```text
CreateWithdrawalUseCase
RefreshSessionUseCase
SettlePaymentUseCase
FailPaymentUseCase
```

### 5.4 Policies

Policies decidem regra de negócio pura.

```text
WithdrawalLimitPolicy
FeePolicy
SessionRenewalPolicy
LedgerPostingPolicy
```

### 5.5 Ports e adapters

```text
VaultClientPort
HttpVaultClientAdapter
MpcSignerPort
LocalDevSignerAdapter
LndInvoicePort
GrpcLndInvoiceAdapter
```

### 5.6 Exceptions

```text
BusinessRuleViolationException
ResourceNotFoundException
UnauthorizedOperationException
ExternalProviderException
IdempotencyConflictException
```

Evitar:

```text
RuntimeException
Exception
IllegalStateException
```

como erro principal de negócio.

---

## 6. Modelo único de erro

Toda resposta de erro pública deve seguir um contrato único.

```json
{
  "code": "WALLET_NOT_FOUND",
  "message": "Wallet not found.",
  "traceId": "trace-123",
  "timestamp": "2026-06-20T00:00:00Z"
}
```

Campos obrigatórios:

```text
code
message
traceId
timestamp
```

Campos opcionais:

```text
details
fieldErrors
retryable
provider
```

### 6.1 Códigos de erro

Categorias oficiais:

```text
VALIDATION_ERROR
AUTHENTICATION_ERROR
AUTHORIZATION_ERROR
BUSINESS_RULE_VIOLATION
RESOURCE_NOT_FOUND
CONFLICT
IDEMPOTENCY_CONFLICT
EXTERNAL_PROVIDER_FAILURE
CONFIGURATION_ERROR
INTERNAL_ERROR
```

### 6.2 Regras obrigatórias

- Controller não deve retornar `e.getMessage()` diretamente.
- Erro externo deve ser traduzido para código interno.
- Stack trace não deve ir para response pública.
- Mensagem interna de banco, Vault, MPC, LND ou Redis não deve ir para cliente.
- `traceId` deve permitir encontrar o log correspondente.

---

## 7. Logs e auditoria

### 7.1 Campos mínimos

Todo log de fluxo crítico deve conter, quando aplicável:

```text
traceId
userId
sessionId
walletId
accountId
transactionId
idempotencyKey
operation
status
provider
latencyMs
```

### 7.2 Eventos financeiros

Eventos financeiros devem ser auditáveis.

Exemplo de nomes:

```text
LEDGER_RESERVATION_REQUESTED
LEDGER_RESERVATION_CREATED
OUTBOX_EVENT_CREATED
OUTBOX_DISPATCH_STARTED
OUTBOX_DISPATCH_SUCCEEDED
OUTBOX_DISPATCH_FAILED
PAYMENT_SETTLED
PAYMENT_FAILED
WITHDRAWAL_SIGNING_REQUESTED
WITHDRAWAL_SIGNING_SUCCEEDED
```

### 7.3 O que não logar

Nunca logar:

- seed;
- private key;
- mnemonic;
- token JWT completo;
- refresh token completo;
- challenge passkey sensível;
- segredo Vault;
- assinatura bruta sensível;
- payload completo de provider com segredo.

---

## 8. Transações

### 8.1 Regra principal

Transação de banco deve proteger estado interno.

Não deve envolver chamada externa lenta ou incerta.

Evitar:

```text
BEGIN TRANSACTION
  salvar ledger
  chamar LND
  chamar Vault
  chamar MPC
COMMIT
```

Preferir:

```text
BEGIN TRANSACTION
  reservar idempotência
  validar invariantes
  salvar ledger
  salvar outbox
COMMIT

worker outbox chama provider externo de forma idempotente
```

### 8.2 Chamada externa

Chamada externa deve ser feita:

- depois do commit interno;
- por outbox quando houver efeito colateral;
- com idempotência;
- com retry controlado;
- com status observável.

### 8.3 Estados intermediários

Estados intermediários devem ser persistidos explicitamente.

Exemplos:

```text
PENDING
RESERVED
DISPATCHING
SETTLED
FAILED
CANCELLED
EXPIRED
```

---

## 9. Idempotência

### 9.1 Regra financeira obrigatória

Nenhuma operação financeira deve chamar provider externo antes de reservar idempotência.

Fluxo obrigatório:

```text
1. validar command
2. normalizar idempotency key
3. reservar idempotency key
4. abrir transação
5. validar invariantes
6. gravar ledger/status/outbox
7. commit
8. executar efeito externo via outbox
9. gravar resultado idempotente
```

### 9.2 Chave idempotente

A chave deve ser escopada por:

```text
userId/accountId
operation
idempotencyKey
requestHash
```

Mesmo `idempotencyKey` com payload diferente deve gerar conflito.

### 9.3 Resultado idempotente

Repetição da mesma request deve retornar o mesmo resultado lógico.

Não deve:

- criar nova transação;
- reservar saldo duas vezes;
- publicar outbox duplicado;
- chamar provider novamente sem dedupe.

---

## 10. Segurança

### 10.1 JWT e sessão

Regras:

- refresh não deve perder roles;
- sessão deve ser revogável;
- token deve carregar versão de sessão ou equivalente;
- mudanças sensíveis exigem step-up;
- tentativa/admin polling não deve virar bearer credential fraco.

### 10.2 Step-up obrigatório

Exigir step-up para:

- alterar credenciais;
- alterar passkey;
- iniciar operação financeira sensível;
- assinar PSBT;
- alterar config de Vault/MPC;
- rotacionar chave;
- acessar admin tokens.

### 10.3 Segredos

- Segredos não entram em logs.
- Segredos não entram em responses.
- Segredos não entram em exceptions públicas.
- Segredos não ficam em config default insegura.
- Fallback local de assinatura só em dev/test e com nome explícito.

---

## 11. Vault, MPC e signer local

### 11.1 Nome honesto de implementação

Se o componente usa chave local única, ele não deve ser chamado de MPC real.

Nomes aceitáveis:

```text
LocalDevSigner
SingleKeyDevSigner
DevOnlySigningAdapter
```

Nomes proibidos para signer local:

```text
ThresholdMpcSigner
ProductionMpcSigner
DistributedSigner
```

### 11.2 Fail-closed

Em produção:

- signer local deve ser proibido;
- ausência de Vault deve falhar startup;
- ausência de MPC real deve falhar operações de assinatura;
- downgrade silencioso é proibido.

---

## 12. Repositories

Repository deve ser responsável por persistência, não por regra de negócio.

Pode:

- salvar entidade;
- buscar por ID;
- buscar por chave única;
- aplicar lock quando solicitado pelo use case;
- retornar projection.

Não pode:

- decidir se saque é permitido;
- calcular taxa;
- chamar provider;
- montar response HTTP;
- engolir erro de constraint como sucesso.

---

## 13. DTOs, commands e domain objects

### 13.1 DTO

DTO pertence à borda HTTP.

```text
CreateWithdrawalRequest
CreateWithdrawalResponse
```

### 13.2 Command

Command pertence à camada application.

```text
CreateWithdrawalCommand
```

### 13.3 Domain object

Domain object representa conceito de negócio.

```text
Withdrawal
LedgerEntry
SatoshiAmount
WalletId
```

Regra:

```text
Controller converte Request -> Command.
UseCase converte Command -> Domain.
UseCase retorna Result/Output.
Controller converte Output -> Response.
```

---

## 14. Testes obrigatórios

### 14.1 Para bug fix

Todo bug fix deve incluir pelo menos um teste que falharia antes.

### 14.2 Para regra financeira

Exigir testes de:

- saldo não negativo;
- reserva única;
- idempotência;
- retry de outbox;
- settlement duplicado;
- failure duplicado;
- concorrência;
- payload diferente com mesma idempotency key.

### 14.3 Para segurança

Exigir testes de:

- token expirado;
- refresh preservando roles;
- sessão revogada;
- ausência de step-up;
- tentativa de acessar recurso de outro usuário;
- admin polling sem autorização suficiente.

### 14.4 Para configuração

Exigir testes de:

- binding de properties de produção;
- ausência de segredo obrigatório;
- fallback dev bloqueado em produção;
- Flyway clean DB;
- migration em ambiente dev/test descartável.

---

## 15. Documentação de código backend

Documentação de código deve explicar intenção, invariantes, riscos e decisões operacionais que não são óbvias pela assinatura.

Documentar sempre:

- controllers públicos;
- use cases públicos;
- ports públicos;
- application services e domain services;
- métodos com regra de negócio;
- métodos com side effect;
- métodos com regra de segurança;
- métodos que emitem auditoria;
- métodos idempotentes;
- decisões fail-closed;
- chamadas para provider externo;
- métodos privados não óbvios.

Evitar:

- comentário que apenas repete o nome do método;
- Javadoc em getter, setter, mapper trivial ou DTO simples;
- documentação genérica sem regra concreta;
- comentários desatualizados para justificar código confuso.

### 15.1 Controllers

Controller público deve documentar o contrato de borda quando o endpoint representa fluxo sensível.

Exemplo:

```java
/**
 * Creates a KFE withdrawal request for the authenticated account.
 *
 * Business rules live in CreateKfeWithdrawalUseCase; this controller only
 * authenticates context, validates request shape and maps the use-case output.
 *
 * Security: requires an authenticated user and step-up already validated by
 * the authorization layer for sensitive KFE operations.
 */
```

### 15.2 Use cases e services

Use case ou service deve documentar regra de negócio, side effects, idempotência e eventos relevantes.

Exemplo KFE:

```java
/**
 * Reserves funds and schedules an outbound KFE transfer.
 *
 * Business rules:
 * - the wallet must be active and owned by the requester;
 * - available balance must cover amount plus fee;
 * - terminal transactions are never reopened.
 *
 * Side effects: persists ledger reservation and creates an outbox event.
 * External calls: none in the database transaction; providers are called by outbox workers.
 */
```

### 15.3 Ports

Port público deve documentar contrato, idempotência esperada, erro interno e restrições de provider.

Exemplo:

```java
/**
 * Submits a signed KFE transaction to the external provider.
 *
 * Implementations must use the provider idempotency token supplied by the
 * caller and translate provider failures to ExternalProviderException without
 * leaking raw provider payloads.
 */
```

### 15.4 Segurança

Documentar a decisão de segurança no ponto em que ela é aplicada ou exigida.

Exemplo:

```java
/**
 * Rejects PSBT signing unless the current session has a valid step-up grant.
 *
 * Security: failure must be deny-by-default; missing, expired or ambiguous
 * step-up state is treated as unauthorized.
 */
```

### 15.5 Idempotência

Documentar reserva, conflito e resultado repetido quando a operação tem efeito financeiro ou provider externo.

Exemplo:

```java
/**
 * Reserves the idempotency key before any provider-visible effect.
 *
 * Replays with the same request hash return the stored result. Replays with a
 * different hash raise IdempotencyConflictException and must not call providers.
 */
```

### 15.6 Auditoria

Documentar eventos auditáveis e payload permitido quando o método emite auditoria.

Exemplo:

```java
/**
 * Emits KFE_TRANSACTION_SUBMITTED after the internal reservation is committed.
 *
 * Audit payload may include traceId, userId, walletId, transactionId and status.
 * It must not include provider secrets, JWTs, raw signatures or private keys.
 */
```

### 15.7 Fail-closed

Documentar explicitamente quando ausência de configuração, autorização, provider ou estado confiável deve negar a operação.

Exemplo:

```java
/**
 * Resolves the production signer adapter.
 *
 * Fail-closed: production must reject startup when Vault or MPC configuration is
 * missing. It must never downgrade silently to a local development signer.
 */
```

## 16. Checklist de refatoração

Antes de alterar código:

```text
[ ] Qual camada estou alterando?
[ ] Existe regra de negócio no lugar correto?
[ ] Existe chamada externa dentro de transação?
[ ] Existe idempotência para efeito colateral?
[ ] Existe erro padronizado?
[ ] Existe log estruturado?
[ ] Existe teste que falha antes?
[ ] Existe risco de expor segredo ou stack trace?
[ ] Existe risco de quebrar contrato público?
```

Depois de alterar código:

```text
[ ] Arquivos alterados estão dentro do escopo.
[ ] Nenhum arquivo não relacionado foi reformatado.
[ ] Teste unitário relevante foi executado.
[ ] Teste de integração foi executado quando necessário.
[ ] Risco residual foi documentado.
[ ] Próximo passo está claro.
```

---

## 17. Contrato para agentes de código

Agentes só devem alterar código quando receberem arquivos exatos.

Prompt obrigatório:

```text
Você é um agente de alteração de código, não de exploração.

Objetivo:
<objetivo exato>

Arquivos que você pode LER:
<lista precisa>

Arquivos que você pode ALTERAR:
<lista precisa>

Você NÃO pode:
- procurar arquivos fora dessa lista;
- usar find/grep global;
- alterar arquivos fora do escopo;
- reformatar arquivos não relacionados;
- corrigir problemas fora do objetivo.

Padrões obrigatórios:
- seguir KEROSENE_BACKEND_ENGINEERING_DESIGN_SYSTEM.md;
- preservar camada correta;
- não retornar e.getMessage() publicamente;
- não chamar provider externo antes de idempotência;
- não introduzir fallback inseguro;
- adicionar ou atualizar teste relevante.

Validação obrigatória:
<comando específico>

Retorno esperado:
- arquivos alterados;
- resumo da mudança;
- teste executado;
- riscos restantes.
```

---

## 18. Regras de merge

Uma alteração não deve ser considerada pronta se:

- mistura controller, regra e infra no mesmo patch sem motivo;
- adiciona exception genérica para fluxo de negócio;
- retorna mensagem interna para cliente;
- faz chamada externa antes de idempotência;
- altera migration sem teste de schema;
- adiciona signer local como se fosse MPC real;
- muda comportamento financeiro sem teste;
- muda segurança sem teste negativo;
- cria config default insegura;
- altera vários módulos sem necessidade.

---

## 19. Ordem recomendada de adoção

1. Padronizar erro público e exception mapping.
2. Padronizar logs estruturados e traceId.
3. Padronizar use cases para fluxos novos.
4. Padronizar idempotência financeira.
5. Separar provider externo via ports.
6. Separar signer local dev/test de MPC real.
7. Adicionar testes de invariantes financeiras.
8. Refatorar services grandes por operação.
9. Bloquear fallback inseguro em produção.
10. Automatizar checklist em revisão/CI quando possível.

---

## 20. Glossário oficial

```text
UseCase
  Classe que executa uma intenção de negócio.

Policy
  Regra de negócio pura e testável.

Port
  Contrato usado pela camada application/domain.

Adapter
  Implementação concreta de um port.

Outbox
  Registro persistente de efeito externo a ser executado com retry seguro.

IdempotencyKey
  Chave que impede duplicidade lógica de uma operação.

Step-up
  Reautenticação forte exigida antes de ação sensível.

Fail-closed
  Comportamento que nega ou aborta quando estado/configuração é incerto.
```

---

## 21. Frase de decisão

Quando houver dúvida, usar esta regra:

```text
O código novo deve ser mais fácil de auditar, testar e negar por segurança do que de executar por acidente.
```

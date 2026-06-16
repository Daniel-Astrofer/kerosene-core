# Kerosene: Resumo Consolidado de Status e Roadmap (Junho 2026)

Este documento consolida as auditorias de documentação e UI, o plano de remoção de mocks e o roadmap de implementação atualizado.

## 1. Sumário Executivo do Projeto

O Kerosene atingiu uma base técnica sólida com identidade visual forte e infraestrutura centralizada. O projeto está em fase de transição de "protótipo avançado" para "pronto para beta", com foco atual em governança de design, integridade de contratos financeiros e remoção de comportamentos simulados (mocks).

**Nota de Maturidade UI:** 7.2 / 10 (Forte identidade, inconsistência em tokens e gaps de privacidade).

---

## 2. Auditoria de Documentação (Canônica)

Toda a documentação de produto foi centralizada na pasta `docs/`. Referências desatualizadas (Hydra v5.0) e duplicatas em subpastas do backend foram removidas.

### Arquivos Canônicos:
- `docs/API_REFERENCE.md`: Referência completa verificada (161 seções HTTP).
- `docs/INFRASTRUCTURE.md`: Infra baseada em Docker Compose, scripts e Spring.
- `docs/APP.md`: Detalhes do app Flutter, rotas e Tor relay.
- `docs/IMPLEMENTATION_NEXT_STEPS.md`: Roadmap priorizado.
- `docs/README.md`: Índice central.

---

## 3. Auditoria de Frontend & UI/UX

### Pontos Fortes:
- Identidade *dark-first* sofisticada (Inter, IBM Plex Serif/Sans Hebrew).
- Fluxos de Auth (Passkey, TOTP) com alta sensação de segurança (8.0/10).
- Mascaramento de saldo e uso de haptics na Home.

### Gaps Críticos:
- **Consistência:** Telas criam tokens locais de cores e raios (radii) em vez de usar o design system global.
- **Internacionalização:** Alta incidência de strings hardcoded em português fora dos arquivos ARB.
- **UX de Pagamentos (6.2/10):** Fluxo permite gerar quotes sem validar o destinatário; linha do tempo de status confusa em caso de erro.
- **Privacidade:** Falta bloqueio global de screenshot, blur no app switcher e limpeza automática de clipboard.

---

## 4. Plano de Implementação de Mocks (Status)

O objetivo é remover simulações de fluxos reais sem afetar o Storybook.

### Progresso:
- **Contas Bitcoin:** Migrado de `LocalBitcoinAccountsService` para `RemoteBitcoinAccountsService` (API Real).
- **Histórico de recebimentos Bitcoin:** `GET /bitcoin/accounts/{accountId}/receive-requests` implementado no backend legado para listar solicitações recentes, omitindo itens ocultos.
- **Transações:** Removido o sucesso fabricado em respostas vazias do ledger; agora falha explicitamente.
- **Preços:** Removidas cotações fixas (USD 65k, BRL 5.0). O app agora exige dados reais do backend.
- **Taxas:** Tamanho estimado de transação agora deriva de dados reais de fee.

### Pendências:
- Ajustar telas Admin para tratar explicitamente estados de erro de `FutureProvider`.

---

## 5. Próximos Passos Priorizados (Roadmap)

### Prioridade 0 (Bloqueantes)
- **Migrações SQL:** Corrigir duplicidade na numeração `V10` do Flyway.
- **Contratos:** Unificar o uso do header `X-Device-Hash` entre frontend e backend.

### Prioridade 1 (Financeiro & Segurança)
- **Idempotência:** Formalizar regra única de `Idempotency-Key` via Redis.
- **Testes de Contrato:** Cobrir todos os endpoints de ledger e pagamentos externos.
- **Privacy Mode:** Implementar política visual para seeds, xpubs e modo de ocultação global.

### Prioridade 2 (Flutter & UX)
- **Redesenho de Pagamentos:** Criar fluxo defensivo (Recipient -> Capabilities -> Quote -> Auth Gate).
- **Consolidação de Tokens:** Unificar raios, sombras e surfaces em um contrato de design único.
- **CI de Linguagem:** Adicionar check que impede strings hardcoded no repositório.

### Prioridade 3 (Infra & Operação)
- **Runbooks:** Criar guias de restore de banco e rotação de Tor keys.
- **Secrets:** Validar checklist de variáveis obrigatórias para o perfil `prod`.

---
*Atualizado em: 15 de Junho de 2026.*

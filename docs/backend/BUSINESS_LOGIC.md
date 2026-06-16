# Lógica de Negócios do Kerosene

## Visão Geral

O backend do Kerosene implementa uma arquitetura sofisticada para gerenciamento de operações financeiras. A lógica central está distribuída em quatro módulos principais: `source.kfe`, `source.treasury`, `source.ledger` e `source.payments`. Cada módulo opera dentro de limites rigorosos para garantir que o sistema permaneça robusto, consistente e logicamente sólido ("cordial").

## Módulos Principais

### 1. KFE (Kerosene Financial Engine)
- **Função:** Gerencia o processamento transacional central, a rede e a execução de transações em diversos trilhos (Lightning, On-chain, Interno).
- **Lógica de Negócios:**
  - Gerencia transferências internas que exigem estritamente autorização transacional via MFA/chave de acesso.
  - Controla o processamento de Quorum por meio de transportes Multi-TLS Tor.
  - Garante validação robusta de rede antes de emitir endereços de recebimento.

### 2. Treasury
- **Função:** Orquestra as reservas de saldo do sistema, retenção de receita e auditoria em larga escala.
- **Lógica de Negócios:**
  - Segrega a carteira operacional do sistema dos fundos dos usuários.
  - Reconcilia a saúde geral da rede, correspondendo os totais internos do razão contra a prova de reservas on-chain.

### 3. Ledger
- **Função:** Sistema de registro oficial para todos os saldos de usuários e entradas transacionais idempotentes.
- **Lógica de Negócios:**
  - Utiliza contabilidade de partidas dobradas para rastrear todos os movimentos de valor.
  - Serviços de auditoria de saldo sombra garantem que os saldos em tempo real correspondam aos extratos históricos e HMACs criptográficos.

### 4. Payments
- **Função:** Gerencia interações externas, gateways fiduciários e links de pagamento.
- **Lógica de Negócios:**
  - Encapsula a execução para lidar com lógica complexa para fornecedores externos.
  - Valida endpoints e impõe limites de taxa rigorosos.

## Verificação

A lógica do sistema foi verificada durante a missão noturna. Pequenos defeitos de sintaxe em testes provenientes de scripts de migração automatizados foram corrigidos, e lacunas de autorização originalmente sinalizadas foram either corrigidas ou formalmente documentadas para revisão futura da arquitetura.

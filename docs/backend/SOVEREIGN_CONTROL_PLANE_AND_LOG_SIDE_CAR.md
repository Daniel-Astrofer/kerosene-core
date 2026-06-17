# Plano Soberano de Controle e Sidecar de Logs

Este documento propõe uma arquitetura para:

- dividir logs entre serviços sem perder auditabilidade;
- operar a plataforma externamente aos servidores de forma segura;
- atualizar código, configuração e migrações sem depender de um operador com acesso bruto;
- manter o sistema autocontido, verificável e resistente a comprometimento local.

O desenho é compatível com a direção atual do Kerosene: shards regionais, KFE como superfície financeira ativa, Vault, MPC sidecar, Tor/Vanguards, trilha de auditoria e processamento por outbox.

## Objetivo

O objetivo não é criar um agregador de logs convencional. O objetivo é criar um plano soberano em que:

1. cada serviço produz seus próprios eventos;
2. um sidecar somente leitura observa, normaliza e sela esses eventos;
3. o controle externo da plataforma acontece por comandos assinados;
4. qualquer mutação operacional ou financeira deixa prova verificável;
5. o sistema continua operando sem depender de um serviço externo central para confiança.

## Princípios

### 1. Fonte da verdade local

Cada servidor, shard ou domínio mantém sua própria trilha local de eventos. A rede pode replicar, consultar ou espelhar, mas não substitui a origem.

### 2. Sidecar somente leitura

O sidecar:

- lê eventos;
- não executa mutações de domínio;
- não escreve em tabelas funcionais;
- não possui credenciais de mutação;
- não altera estado financeiro, de autorização ou de implantação.

### 3. Controle por comando assinado

Atualização, restart, drain, migração, rotação e manutenção são ações descritas como comandos assinados. O servidor só executa após validar:

- autenticidade da assinatura;
- validade temporal;
- política de autorização;
- idempotência;
- compatibilidade com o estado atual.

### 4. Auditoria encadeada

Eventos de auditoria não devem ser tratáveis como texto mutável. Devem ser:

- estruturados;
- append-only;
- encadeados por hash;
- selados por lote;
- reprodutíveis por verificação independente.

### 5. Soberania operacional

O sistema não deve depender de um SaaS de observabilidade, painel externo ou operador com acesso irrestrito ao host. O modelo deve sobreviver a:

- indisponibilidade de provedor;
- bloqueio de rede;
- comprometimento parcial de um nó;
- perda de conectividade com a interface de operação.

## Divisão de logs entre serviços

Eu separaria os logs em quatro classes.

### 1. Logs operacionais

Uso:

- debug;
- health;
- retry;
- filas;
- latência;
- eventos de vida do processo.

Propriedades:

- volume alto;
- retenção curta;
- não são a prova principal;
- podem ser compactados e rotacionados.

### 2. Logs de segurança

Uso:

- login;
- logout;
- TOTP;
- passkey;
- recuperação;
- troca de credencial;
- bloqueio de dispositivo;
- falhas de autorização.

Propriedades:

- estruturados;
- imutáveis;
- consultáveis por identidade, dispositivo e sessão;
- sensíveis a mascaramento.

### 3. Logs financeiros e de auditoria

Uso:

- criação de link de pagamento;
- alocação de endereço;
- emissão de transação;
- broadcast;
- confirmação;
- reversão;
- reconciliação;
- cálculo de taxa;
- eventos de outbox;
- mudanças de saldo.

Propriedades:

- append-only;
- hash encadeado;
- selados por lote;
- verificáveis em réplica;
- nunca editados “no lugar”.

### 4. Logs de controle da plataforma

Uso:

- deploy;
- rollback;
- atualização de código;
- atualização de configuração;
- rotação de chaves;
- mudança de política;
- manutenção;
- lockdown;
- retomada.

Propriedades:

- comandados por assinatura;
- executados com confirmação;
- sempre auditados;
- com identidade de operador e quorum.

## Sidecar readonly

### Função

O sidecar é um observador local de confiança mínima. Ele captura eventos do serviço principal ou de uma fila local e os converte em um fluxo auditável.

### Responsabilidades

- receber eventos estruturados;
- remover dados sensíveis por política;
- aplicar fingerprint/hashing;
- escrever em storage append-only local;
- expor consulta readonly;
- produzir checkpoint de integridade;
- entregar prova para auditoria externa ou interna.

### O que ele não faz

- não aceita comandos de mutação;
- não atualiza schema de negócio;
- não assina operações financeiras;
- não publica segredos;
- não substitui o backend principal.

## Modelo de integração

### Opção recomendada

Usar o sidecar por serviço ou por shard, com um endpoint local autenticado e um writer de eventos dedicado no processo principal.

Fluxo:

1. o serviço principal emite evento estruturado;
2. o evento vai para um buffer local ou outbox;
3. o sidecar consome o evento;
4. o sidecar sela o registro;
5. o sidecar expõe consulta readonly e snapshots;
6. o backend pode validar os selos periodicamente.

### Vantagem

- menor acoplamento;
- falha do sidecar não derruba o core;
- melhor isolamento de privilégio;
- fácil de auditar.

## Integridade e prova

Para auditoria séria, eu não dependeria apenas de logs assinados individualmente. Eu usaria três camadas:

### 1. Hash por evento

Cada evento recebe:

- `eventId`
- `timestamp`
- `service`
- `region`
- `shard`
- `sequence`
- `payloadHash`
- `prevHash`

Isso cria um encadeamento local.

### 2. Merkle por lote

A cada lote ou janela:

- o sidecar calcula uma raiz Merkle;
- publica o root internamente;
- persiste a raiz junto ao intervalo de sequência.

Isso permite prova eficiente sem reprocessar tudo.

### 3. Assinatura do checkpoint

O checkpoint é assinado por uma chave de auditoria isolada:

- idealmente em Vault;
- nunca em texto claro no processo;
- com rotação e revogação.

## Controle externo da plataforma

### Modelo recomendado

O controle externo deve usar um **control plane soberano**. Isso significa que a operação remota não é acesso livre ao servidor. É um protocolo de comando.

### Fluxo

1. um operador cria um comando;
2. o comando é assinado;
3. o comando recebe validade curta;
4. o servidor verifica assinatura e política;
5. o servidor aplica ou rejeita;
6. a decisão é registrada em log imutável.

### Comandos típicos

- `deploy_artifact`
- `rollback_artifact`
- `rotate_secrets`
- `drain_node`
- `freeze_shard`
- `unfreeze_shard`
- `run_migration`
- `rebuild_indexes`
- `refresh_audit_checkpoint`

### Regras

- comandos de mutação expiram rapidamente;
- ações críticas exigem quorum;
- toda execução é idempotente;
- toda recusa também é auditada;
- o servidor nunca confia só na origem da rede.

## Atualização de código

Atualização de código deve ser tratada como um tipo de comando soberano.

### Pipeline recomendado

1. construir artefato ou imagem;
2. assinar artefato;
3. publicar manifesto de versão;
4. verificar hash no destino;
5. executar pré-checagens;
6. aplicar migração, se necessária;
7. reiniciar/recarregar;
8. validar saúde;
9. registrar root de mudança.

### Requisitos

- atualização determinística;
- rollback formal;
- sem “git pull” direto no servidor;
- sem dependência de painel que tenha poder irrestrito;
- sem segredo persistente no host para aprovar o deploy.

### Migrações

Migrações precisam ser:

- versionadas;
- auditas;
- compatíveis com rollback quando possível;
- executadas sob comando assinado;
- registradas no sidecar.

## Resiliência a comprometimento

O projeto precisa assumir que um nó pode ser comprometido. Então:

- o nó isolado não deve conseguir alterar a autoridade global;
- o nó não deve conseguir apagar sua própria trilha de forma útil;
- o lado de controle não deve depender de um único token;
- o sidecar não deve ter permissão para mutar nada;
- chaves devem ser separadas por função e por shard.

## Medidas de endurecimento

- filesystem readonly onde possível;
- `no-new-privileges`;
- identidade de serviço mínima;
- sockets locais restritos;
- mTLS ou canal autenticado local;
- rotação de segredos;
- limitação forte de payload;
- retenção e compactação controladas;
- desligamento seguro com flush de checkpoint.

## Modelo de soberania

Se o sistema quiser se aproximar da ideia de autocontenção da rede Bitcoin, ele precisa evitar qualquer dependência central obrigatória para confiança.

Isso implica:

- múltiplos nós capazes de verificar o mesmo estado;
- artefatos verificáveis;
- logs com prova independente;
- comando com quorum;
- funcionamento degradado sem autoridade remota única;
- zero confiança implícita na rede ou no provedor de hospedagem.

## Recomendação de implementação no Kerosene

Eu implementaria em três fases.

### Fase 1: base de auditoria

- padronizar eventos de controle e auditoria;
- definir esquema de evento;
- adicionar hash encadeado;
- emitir checkpoints;
- separar logs financeiros, de segurança e operacionais.

### Fase 2: sidecar readonly

- criar sidecar para leitura e selagem;
- expor consulta readonly;
- adicionar verificação de checkpoint;
- integrar com KFE, Vault e outbox.

### Fase 3: control plane soberano

- definir comando assinado;
- criar validação de quorum;
- formalizar deploy, rollback e migração;
- registrar todas as mutações da plataforma.

## Decisão prática

Se a meta é um sistema realmente soberano e auditável, o desenho certo é:

- serviço principal executa a lógica;
- sidecar readonly testemunha e sela;
- controle externo é por comando assinado;
- atualização de código é uma operação formal;
- logs não são “telemetria”, são prova.


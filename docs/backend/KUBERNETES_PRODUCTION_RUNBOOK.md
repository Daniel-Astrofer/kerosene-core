# Kerosene Kubernetes Production Runbook

Este runbook define como transformar a infraestrutura atual do Kerosene em uma implantação Kubernetes robusta, escalável, segura e operável em produção. O objetivo é que os containers possam ser reiniciados, substituídos, escalados, depurados e recuperados sem perda de estado crítico nem abertura indevida de superfície de ataque.

## Objetivos

- Rodar `kerosene-app`, `mpc-sidecar`, `web-admin`, Postgres, Redis, Vault, Bitcoin Core, LND, Tor, Vanguards e observabilidade em Kubernetes.
- Permitir restart/rollout/reset de instâncias sem corromper estado.
- Escalar componentes stateless horizontalmente.
- Manter componentes stateful em StatefulSets com PVCs, backups e procedimentos de restore.
- Ter método padronizado de debug: logs, eventos, métricas, traces, shell efêmero, dump controlado, port-forward e inspeção de rede.
- Operar com segurança: non-root, capabilities mínimas, secrets fora do Git, NetworkPolicy default-deny, RBAC mínimo, imagens fixadas e auditáveis.

## Modelo de workload

| Componente | Tipo Kubernetes | Escala | Estado | Observações |
| --- | --- | --- | --- | --- |
| `kerosene-app` | Deployment | Horizontal | Stateless/local ephemeral | Pode escalar com HPA. Estado deve ficar em Postgres, Redis, Vault, LND/Bitcoin ou serviços externos. |
| `web-admin` | Deployment | Horizontal | Stateless | Servir build Flutter via Nginx. |
| `mpc-sidecar-{region}` | Deployment ou StatefulSet | Por shard | Segredos/volumes sensíveis | Preferir StatefulSet se houver shard local persistente por instância. |
| Postgres | StatefulSet via Operator | Vertical + HA | Persistente | Preferir CloudNativePG/Zalando Operator em produção. |
| Redis | StatefulSet/Operator | HA | Persistente/efêmero controlado | Usar Redis HA/Sentinel/Operator. |
| Vault Raft | StatefulSet | 3 ou 5 réplicas | Persistente | Bootstrapping e unseal devem ser operacionais e auditáveis. |
| Bitcoin Core | StatefulSet | 1 por rede/região | Persistente grande | PVC dedicado, RPC interno, ZMQ interno. |
| LND | StatefulSet | 1 por nó lógico | Persistente crítico | Wallet, canais, macaroon e TLS são críticos. |
| Tor/Vanguards | StatefulSet ou Deployment | Por shard | Chaves Onion persistentes | Hidden service keys precisam de PVC/secret seguro. |
| Prometheus/Grafana | Helm chart/operator | HA opcional | Persistente | kube-prometheus-stack recomendado. |

## Estrutura recomendada no repositório

```text
infra/kubernetes/
├── base/
│   ├── namespace.yaml
│   ├── policies/
│   ├── kerosene-app/
│   ├── mpc-sidecar/
│   ├── web-admin/
│   ├── postgres/
│   ├── redis/
│   ├── vault/
│   ├── bitcoin/
│   ├── lnd/
│   ├── tor/
│   └── observability/
├── overlays/
│   ├── local/
│   ├── staging/
│   └── production/
└── scripts/
    ├── deploy-local.sh
    ├── deploy-staging.sh
    ├── deploy-production.sh
    ├── debug-pod.sh
    ├── collect-diagnostics.sh
    └── emergency-rollback.sh
```

Usar Kustomize para ambientes. Evitar YAML duplicado por ambiente.

## Regras de imagem

Produção não deve usar `latest`.

Formato aceitável:

```yaml
image: registry.example.com/kerosene/kerosene-app:2026.06.23-a1b2c3d
```

Formato preferido:

```yaml
image: registry.example.com/kerosene/kerosene-app@sha256:<digest>
```

Regras:

- Toda imagem deve ser buildada em CI.
- Toda imagem deve passar por teste e scan.
- Toda imagem deve ser assinada, preferencialmente com Cosign.
- O cluster deve bloquear imagens sem digest/assinatura em produção via admission policy.

## Padrão mínimo para Deployment stateless

Todo Deployment stateless deve ter:

- `replicas` mínimo 2 em produção.
- `readinessProbe`.
- `livenessProbe`.
- `startupProbe` para boot lento.
- `resources.requests` e `resources.limits`.
- `securityContext` non-root.
- `readOnlyRootFilesystem` quando possível.
- `allowPrivilegeEscalation: false`.
- `capabilities.drop: ["ALL"]`.
- `seccompProfile: RuntimeDefault`.
- `PodDisruptionBudget`.
- `Service` interno.
- `NetworkPolicy` específica.
- `ServiceAccount` próprio.

Exemplo base para `kerosene-app`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kerosene-app
  namespace: kerosene-production
spec:
  replicas: 3
  revisionHistoryLimit: 10
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: kerosene-app
  template:
    metadata:
      labels:
        app.kubernetes.io/name: kerosene-app
    spec:
      serviceAccountName: kerosene-app
      terminationGracePeriodSeconds: 60
      securityContext:
        runAsUser: 65532
        runAsGroup: 65532
        runAsNonRoot: true
        seccompProfile:
          type: RuntimeDefault
      containers:
        - name: kerosene-app
          image: registry.example.com/kerosene/kerosene-app@sha256:<digest>
          imagePullPolicy: IfNotPresent
          ports:
            - name: http
              containerPort: 8080
          envFrom:
            - configMapRef:
                name: kerosene-app-config
          env:
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: kerosene-db-secrets
                  key: application-password
          startupProbe:
            httpGet:
              path: /health/ready
              port: http
            periodSeconds: 5
            failureThreshold: 90
          readinessProbe:
            httpGet:
              path: /health/ready
              port: http
            periodSeconds: 10
            timeoutSeconds: 3
            failureThreshold: 3
          livenessProbe:
            httpGet:
              path: /health/live
              port: http
            periodSeconds: 20
            timeoutSeconds: 3
            failureThreshold: 3
          lifecycle:
            preStop:
              exec:
                command: ["/bin/sh", "-c", "sleep 20"]
          resources:
            requests:
              cpu: "1000m"
              memory: "2Gi"
            limits:
              cpu: "2000m"
              memory: "4Gi"
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false
            capabilities:
              drop: ["ALL"]
```

Nota: se a imagem for distroless, `preStop` com shell não funciona. Nesse caso usar apenas `terminationGracePeriodSeconds`, endpoint de graceful shutdown se existir, ou uma imagem com entrypoint que suporte hook. Não adicionar shell apenas para debug em imagem de produção.

## Service e exposição

`kerosene-app` deve ter Service interno:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: kerosene-app
  namespace: kerosene-production
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: kerosene-app
  ports:
    - name: http
      port: 8080
      targetPort: http
```

Exposição externa deve ser feita por Ingress Controller ou Gateway API, com TLS, rate limit e WAF quando aplicável. Vault, Redis, Postgres, MPC, Bitcoin RPC e LND gRPC não devem ser expostos publicamente.

## Escala

### Escalar stateless manualmente

```bash
kubectl -n kerosene-production scale deployment/kerosene-app --replicas=6
```

### Autoescala HPA

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kerosene-app
  namespace: kerosene-production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kerosene-app
  minReplicas: 3
  maxReplicas: 12
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 65
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 75
```

Para app financeiro, não escalar worker crítico sem garantir idempotência, locking distribuído e ausência de processamento duplicado. Jobs/outbox devem ser idempotentes.

## Reset seguro de instâncias

### Reset de Pod stateless

Usar quando a instância travou, memory leakou ou ficou fora de readiness.

```bash
kubectl -n kerosene-production delete pod -l app.kubernetes.io/name=kerosene-app
```

O ReplicaSet recria os Pods.

### Restart controlado do Deployment

```bash
kubectl -n kerosene-production rollout restart deployment/kerosene-app
kubectl -n kerosene-production rollout status deployment/kerosene-app
```

### Rollback

```bash
kubectl -n kerosene-production rollout history deployment/kerosene-app
kubectl -n kerosene-production rollout undo deployment/kerosene-app
kubectl -n kerosene-production rollout status deployment/kerosene-app
```

### Reset de StatefulSet

Cuidado: StatefulSet tem identidade e volume.

Reiniciar sem apagar dados:

```bash
kubectl -n kerosene-production rollout restart statefulset/bitcoin-core
kubectl -n kerosene-production rollout status statefulset/bitcoin-core
```

Apagar Pod mantendo PVC:

```bash
kubectl -n kerosene-production delete pod bitcoin-core-0
```

Nunca apagar PVC de Postgres, Vault, LND ou Bitcoin sem procedimento formal de backup/restore.

### Reset com cordon/drain de node

```bash
kubectl cordon <node>
kubectl drain <node> --ignore-daemonsets --delete-emptydir-data
```

Depois de corrigir:

```bash
kubectl uncordon <node>
```

## Debug padrão

### Ver Pods

```bash
kubectl -n kerosene-production get pods -o wide
```

### Ver detalhes de um Pod

```bash
kubectl -n kerosene-production describe pod <pod>
```

### Ver logs atuais

```bash
kubectl -n kerosene-production logs <pod> -c kerosene-app --tail=200
```

### Seguir logs

```bash
kubectl -n kerosene-production logs -f deployment/kerosene-app -c kerosene-app --tail=200
```

### Ver logs da instância anterior após crash

```bash
kubectl -n kerosene-production logs <pod> -c kerosene-app --previous --tail=300
```

### Eventos do namespace

```bash
kubectl -n kerosene-production get events --sort-by=.lastTimestamp
```

### Ver YAML real aplicado

```bash
kubectl -n kerosene-production get deployment kerosene-app -o yaml
```

### Entrar em container com shell

Só funciona se a imagem tiver shell:

```bash
kubectl -n kerosene-production exec -it <pod> -c <container> -- sh
```

A imagem `kerosene-app` é distroless, então normalmente não terá shell. Para debug seguro, usar ephemeral container.

### Debug em imagem distroless com ephemeral container

```bash
kubectl -n kerosene-production debug -it <pod> \
  --target=kerosene-app \
  --image=nicolaka/netshoot:latest \
  --share-processes
```

Em produção, a imagem de debug deve ser fixada por digest e permitida por RBAC apenas para operadores autorizados. O debug container não deve virar padrão de runtime.

### Port-forward temporário

```bash
kubectl -n kerosene-production port-forward svc/kerosene-app 18080:8080
curl http://127.0.0.1:18080/health/ready
```

### Teste DNS dentro do cluster

```bash
kubectl -n kerosene-production run dns-debug --rm -it \
  --image=busybox:1.36 \
  --restart=Never -- nslookup kerosene-app
```

### Teste HTTP interno

```bash
kubectl -n kerosene-production run curl-debug --rm -it \
  --image=curlimages/curl:8.10.1 \
  --restart=Never -- curl -v http://kerosene-app:8080/health/ready
```

## Coleta de diagnóstico

Criar script `collect-diagnostics.sh` com:

```bash
#!/usr/bin/env bash
set -euo pipefail
NS="${1:-kerosene-production}"
OUT="diagnostics-${NS}-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$OUT"

kubectl -n "$NS" get all -o wide > "$OUT/all.txt"
kubectl -n "$NS" get pods -o yaml > "$OUT/pods.yaml"
kubectl -n "$NS" get events --sort-by=.lastTimestamp > "$OUT/events.txt"
kubectl -n "$NS" get configmap -o yaml > "$OUT/configmaps.yaml"
kubectl -n "$NS" get pvc -o wide > "$OUT/pvc.txt"
kubectl -n "$NS" top pods > "$OUT/top-pods.txt" || true
kubectl top nodes > "$OUT/top-nodes.txt" || true

for pod in $(kubectl -n "$NS" get pods -o name); do
  safe_name="${pod//\//_}"
  kubectl -n "$NS" describe "$pod" > "$OUT/${safe_name}-describe.txt" || true
  kubectl -n "$NS" logs "$pod" --all-containers --tail=500 > "$OUT/${safe_name}-logs.txt" || true
  kubectl -n "$NS" logs "$pod" --all-containers --previous --tail=500 > "$OUT/${safe_name}-previous-logs.txt" 2>/dev/null || true
done

tar -czf "$OUT.tar.gz" "$OUT"
echo "$OUT.tar.gz"
```

Não coletar Secrets em texto puro. Se necessário, coletar apenas nomes, não valores.

## NetworkPolicy

Produção deve começar com default deny:

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: default-deny-all
  namespace: kerosene-production
spec:
  podSelector: {}
  policyTypes:
    - Ingress
    - Egress
```

Depois liberar fluxo por fluxo:

- Ingress Controller -> `kerosene-app` HTTP.
- `kerosene-app` -> Postgres.
- `kerosene-app` -> Redis.
- `kerosene-app` -> MPC sidecar regional.
- `kerosene-app` -> Vault.
- `kerosene-app` -> LND.
- `kerosene-app` -> Bitcoin RPC/ZMQ se necessário.
- Prometheus -> `/actuator/prometheus`.
- Tor -> internet egress.
- Bitcoin Core -> internet/P2P Bitcoin.

Tudo que não estiver nesta lista deve ficar bloqueado.

## Secrets

Não commitar Secrets.

Opções recomendadas:

1. External Secrets Operator com provedor externo.
2. Sealed Secrets.
3. SOPS + age em GitOps.
4. Vault Agent Injector para workloads que consomem Vault diretamente.

Secrets críticos:

- JWT secret/private key.
- Postgres passwords.
- Redis password.
- LND macaroon/TLS/wallet material.
- Bitcoin RPC password.
- Vault tokens/unseal flow.
- MPC master key/material.
- Tor hidden service keys.
- Release signing keys.

## Segurança de Pod

Aplicar Pod Security `restricted` no namespace:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: kerosene-production
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

Exceções como Vault `IPC_LOCK` devem ser isoladas, justificadas e controladas por namespace/policy específica.

## Observabilidade

Instalar `kube-prometheus-stack` e criar `ServiceMonitor` para o backend:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: kerosene-app
  namespace: kerosene-production
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: kerosene-app
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
```

Alertas mínimos:

- Pod CrashLoopBackOff.
- Readiness falhando por mais de 2 minutos.
- Latência HTTP alta.
- 5xx alto.
- CPU throttling.
- Memória perto do limite.
- PVC acima de 80%.
- Postgres indisponível.
- Redis indisponível.
- Vault sealed ou indisponível.
- LND locked/down.
- Bitcoin Core sem sync.
- Rollout travado.

## Deploy seguro

Usar rolling update com `maxUnavailable: 0` para API.

Passos:

```bash
kubectl -n kerosene-production apply -k infra/kubernetes/overlays/production
kubectl -n kerosene-production rollout status deployment/kerosene-app
kubectl -n kerosene-production get pods -o wide
```

Smoke test:

```bash
kubectl -n kerosene-production port-forward svc/kerosene-app 18080:8080
curl -f http://127.0.0.1:18080/health/ready
curl -f http://127.0.0.1:18080/system/release
```

## Ordem de implementação

1. Reorganizar manifests existentes em `k8s/base` e `k8s/overlays`.
2. Corrigir `kerosene-app` com Service, probes, HPA, PDB, ServiceAccount e NetworkPolicy.
3. Criar manifests do `mpc-sidecar` por região.
4. Criar stack local/staging mínima com Postgres, Redis, app, MPC e web-admin.
5. Adicionar observabilidade.
6. Adicionar NetworkPolicies default-deny.
7. Adicionar External Secrets/Sealed Secrets/SOPS.
8. Migrar Vault Raft.
9. Migrar Bitcoin Core.
10. Migrar LND.
11. Migrar Tor/Vanguards.
12. Adicionar CI/CD com build, test, scan, sign e deploy GitOps.
13. Criar runbooks de backup/restore para Postgres, Vault, Bitcoin e LND.
14. Testar queda de Pod, queda de Node, rollback, restauração de backup e rotação de Secrets.

## Critérios de pronto para produção

A implantação só deve ser considerada pronta quando:

- Nenhum workload usa `latest`.
- Todos os Deployments têm probes e resource limits.
- Todos os statefuls têm PVC e backup documentado.
- Existe default-deny NetworkPolicy.
- Secrets não estão no Git.
- Rollback foi testado.
- Restart de Pod foi testado.
- Restore de Postgres foi testado.
- Vault unseal/bootstrap foi testado.
- LND recovery foi testado em ambiente seguro.
- Observabilidade e alertas estão ativos.
- CI/CD gera imagem por commit, escaneia e assina.
- Debug via ephemeral container está restrito por RBAC.
- Operadores sabem coletar diagnóstico sem vazar Secrets.

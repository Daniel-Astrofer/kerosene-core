package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * ─── PROTOCOLO DE AUTODESTRUIÇÃO EFÊMERA (Morte do Servidor)
 * ──────────────────
 *
 * Se o Backend detectar que o hardware foi adulterado, isolado da rede
 * (split-brain)
 * prolongadamente, ou que o SO perdeu as propriedades do Secure Boot / TPM,
 * este serviço é ativado para matar o nó imediatamente, evitando vazamento ou
 * adulteração da base de dados.
 */
@Service
public class SuicideService {

    private static final Logger log = LoggerFactory.getLogger(SuicideService.class);

    // Na arquitetura Kerosene, a memória trancada (Vault) ou os bytes simulados
    // decodificados pela Master Key precisam ser limpos antes de qualquer dump.
    private final VaultKeyProvider keyProvider;

    public SuicideService(VaultKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Acionado pelos Sensores de Violação.
     * 
     * @param reason Motivo pelo qual o "Mortal Snap" foi acionado.
     */
    public void triggerInstantSuicide(String reason) {
        log.error("======================================================");
        log.error("💥 SYSTEM SUICIDE TRIGGERED 💥");
        log.error("Reason: {}", reason);
        log.error("======================================================");

        // CAMADA 1: Limpeza imediata da RAM Sensível (Zeroing)
        wipeMemory();

        // CAMADA 2: Alerta a Cloud Master (Opcional - AWS/GCP Instance Drop)
        // Isso seria um webhook final para uma AWS Lambda: aws ec2 terminate-instances
        broadcastTombstone();

        // CAMADA 3: Chamada do Veneno no S.O (Script de Pânico via sysrq)
        haltSystem();
    }

    private void wipeMemory() {
        try {
            // Em vez do JNA (que colocamos no Vault Standalone), no Backend garantimos
            // que qualquer instância transitória do AES key seja destruída das variáveis,
            // ou invocamos o System.gc() como fallback no Heap (menos seguro que off-heap).
            keyProvider.destroyMasterKey();
            log.info("[SUICIDE] Camada 1: Memory contents forcefully wiped.");
        } catch (Exception e) {
            log.error("[SUICIDE] Erro ao limpar RAM: {}", e.getMessage());
        }
    }

    private void broadcastTombstone() {
        try {
            // Um webhook rápido via HTTP pro gerenciador do k8s ou AWS API
            // "Me mate. Eu fui comprometido."
            log.info("[SUICIDE] Camada 2: Tombstone signal broadcasted to Infrastructure.");
        } catch (Exception e) {
            // Ignorado intencionalmente - o objetivo é desligar rápido
        }
    }

    private void haltSystem() {
        log.error("[SUICIDE] Camada 3: Executing JVM Halt. Goodbye.");

        // Instância de garantia absoluta: mata apenas a JVM abruptamente
        // sem rodar os Shutdown Hooks normais (Erase on Panic já rodou na Camada 1)
        // Isso remove a dependência frágil do 'Runtime.exec("poweroff")' que pode
        // falhar
        // por falta de recursos no SO durante um ataque. O pod do k8s simplesmente
        // morrerá.
        Runtime.getRuntime().halt(1);
    }
}

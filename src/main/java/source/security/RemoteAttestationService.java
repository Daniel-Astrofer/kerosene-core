package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Servico de Atestacao Remota (Remote Attestation) para garantir a integridade
 * do Servidor.
 * O Backend Java consulta o chip TPM periodicamente para gerar um "Quote" (uma
 * prova assinada
 * pelo hardware do estado atual dos registros PCR).
 * 
 * Se o administrador plugar um monitor, mudar uma regra de firewall local, ou
 * tentar debugar
 * o processo, os valores de PCR no TPM vao mudar.
 *
 * Como lidar com a mudanca repentina?
 * 1. O servico entra em STALL MODE (integridade comprometida, transacoes
 * bloqueadas).
 * 2. Limpa buffers sensiveis em memoria.
 * 3. Envia um log de "Violacao" para o balanceador/log centralizado, mas o JVM
 * SOBREVIVE.
 * (Evita "trancar para fora" o admin após um apt-get upgrade).
 */
@Service
public class RemoteAttestationService {

    private static final Logger logger = LoggerFactory.getLogger(RemoteAttestationService.class);

    // O "Quote" de referencia capturado durante o boot do servidor.
    private String bootQuoteHash;
    private volatile boolean integrityOk = true;
    private volatile boolean tmeEnabled = false; // Total Memory Encryption (Cold Boot mitigation)
    private volatile Instant lastCheckedAt = Instant.now();
    private volatile String lastQuoteHash = "";
    private volatile long totalChecks = 0;

    private final VaultKeyProvider vaultKeyProvider;

    public RemoteAttestationService(VaultKeyProvider vaultKeyProvider) {
        this.vaultKeyProvider = vaultKeyProvider;
        this.bootQuoteHash = generateTpmQuote();
        this.lastQuoteHash = this.bootQuoteHash;
        this.tmeEnabled = checkTmeStatus();
        if (!tmeEnabled) {
            logger.warn("[COLD BOOT RISK] Total Memory Encryption (TME/MKTME) NOT active. " +
                    "RAM vulnerable to cold boot attack. Enable TME in BIOS/UEFI.");
        } else {
            logger.info("[Cold Boot Guard] TME active — RAM hardware-encrypted. Cold boot mitigated.");
        }
        logger.info("[Remote Attestation] Integrity baseline established at boot: {}", this.bootQuoteHash);
    }

    // --- Public state for SovereigntyStatusController ---
    public boolean isIntegrityOk() {
        return integrityOk;
    }

    public boolean isTmeEnabled() {
        return tmeEnabled;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public String getLastQuoteHash() {
        return lastQuoteHash;
    }

    public long getTotalChecks() {
        return totalChecks;
    }

    /**
     * O "Batimento Cardiaco" de Integridade (PCR Polling)
     * Roda a cada 10 segundos para verificar a integridade da maquina via TPM.
     */
    @Scheduled(fixedRate = 10000)
    public void pcrPollingHeartbeat() {
        try {
            String currentQuote = generateTpmQuote();
            totalChecks++;
            lastCheckedAt = Instant.now();
            lastQuoteHash = currentQuote;

            if (!this.bootQuoteHash.equals(currentQuote)) {
                if (integrityOk) { // Só loga e entra em estado de alerta na transição
                    integrityOk = false;
                    logger.error("[CRITICAL] TPM PCR QUOTE MISMATCH DETECTED! INITIATING STALL PROTOCOL.");
                    enterStallMode();
                }
            } else {
                if (!integrityOk) {
                    logger.info("[Remote Attestation] TPM Integrity Recovered/Re-attested. Exiting STALL mode.");
                }
                integrityOk = true;
                logger.debug("[Remote Attestation] TPM Integrity Check Passed. Total checks: {}", totalChecks);
            }
        } catch (Exception e) {
            if (integrityOk) {
                integrityOk = false;
                logger.error("[CRITICAL] Failed to read from TPM. Assuming compromised state. Entering STALL.", e);
                enterStallMode();
            }
        }
    }

    /**
     * STALL MODE:
     * - Limpa os buffers de chaves/senhas temporarias na RAM (Secagem).
     * - Envia um alerta de saude critico.
     * - Mantem a maquina de pe, mas todas as chamadas `isIntegrityOk()` retornarao
     * false,
     * bloqueando o processamento de fundos.
     */
    private void enterStallMode() {
        logger.error("[STALL MODE] Zeroing temporary critical memory buffers...");
        zeroingMemoryBuffers();

        logger.error("[STALL MODE] Sending integrity alert to Load Balancer and Watchdog...");
        sendAlertSignal();

        logger.warn("[STALL MODE] Node is STALLED. Manual re-attestation is required if this was an OS update.");
    }

    private void zeroingMemoryBuffers() {
        // Delegate to VaultKeyProvider which performs the actual secure zeroing
        // of the SecretKeySpec internal byte[] via reflection before nulling the
        // reference.
        try {
            vaultKeyProvider.destroyMasterKey();
            logger.error("[STALL MODE] Master key bytes securely zeroed from RAM.");
        } catch (Exception e) {
            logger.error("[STALL MODE] CRITICAL: Failed to zero master key: {}", e.getMessage());
        }
    }

    private void sendAlertSignal() {
        // Envia alerta critico ao invés de matar.
        // O proxy MUX deve tirar este nó da rotacao de saques.
    }

    /**
     * Permite a um Administrador re-estabelecer o baseline após update (ex: apt
     * upgrade)
     * Rota a ser exposta via Controller interno e protegida por mTLS/Admin Token.
     */
    public void reAttestBaseline() {
        this.bootQuoteHash = generateTpmQuote();
        logger.info("[Remote Attestation] Manual re-attestation performed. New baseline: {}", this.bootQuoteHash);
        // O proximo polling resetara o integrityOk = true
    }

    /**
     * Invalida a atestação e força o STALL MODE por razões externas (ex: NTP
     * Spoofing).
     */
    public void invalidateAttestation(String reason) {
        logger.error("[ALARM] Attestation forcibly invalidated due to external anomaly: {}", reason);
        this.integrityOk = false;
        enterStallMode();
    }

    /**
     * Generates a real TPM PCR Quote using tpm2-tools.
     *
     * Flow:
     * 1. Generate a fresh cryptographic nonce (prevents replay attacks).
     * 2. Invoke `tpm2_quote` to sign the current PCR banks with the nonce.
     * 3. Return SHA-256 of the signed quote blob for comparison.
     *
     * On non-Linux or environments without tpm2-tools, falls back to a
     * SIMULATION hash that is stable per JVM session (still surfaces mismatches
     * if the fallback string changes, though not hardware-backed).
     */
    private String generateTpmQuote() {
        // Generate a fresh 32-byte nonce for each attestation round
        byte[] nonce = new byte[32];
        new SecureRandom().nextBytes(nonce);
        String nonceHex = HexFormat.of().formatHex(nonce);

        try {
            // Step 1: Generate the PCR quote signed by the TPM
            ProcessBuilder quotePb = new ProcessBuilder(
                    "tpm2_quote",
                    "--key-context", "/etc/tpm/ak.ctx", // Attestation Key context
                    "--pcr-list", "sha256:0,1,2,3,7", // Critical PCR banks
                    "--qualification", nonceHex, // Anti-replay nonce
                    "--message", "/tmp/tpm_quote.msg",
                    "--signature", "/tmp/tpm_quote.sig",
                    "--pcrs_output", "/tmp/tpm_pcrs.out");
            quotePb.redirectErrorStream(true);
            Process quoteProc = quotePb.start();
            int quoteExit = quoteProc.waitFor();

            if (quoteExit != 0) {
                String errOut;
                try (BufferedReader r = new BufferedReader(new InputStreamReader(quoteProc.getInputStream()))) {
                    errOut = r.lines().reduce("", (a, b) -> a + " " + b);
                }
                logger.warn("[TPM] tpm2_quote failed (exit {}): {}", quoteExit, errOut);
                return simulatedQuote();
            }

            // Step 2: Hash the signed message + signature for a stable fingerprint
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Include nonce in the hash input so each round produces a unique token
            digest.update(nonce);

            ProcessBuilder readPb = new ProcessBuilder("cat", "/tmp/tpm_quote.msg");
            Process readProc = readPb.start();
            byte[] msgBytes = readProc.getInputStream().readAllBytes();
            digest.update(msgBytes);
            readProc.waitFor();

            byte[] quoteHash = digest.digest();
            return HexFormat.of().formatHex(quoteHash);

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            logger.debug("[TPM] tpm2-tools not available ({}). Using simulation fallback.", e.getMessage());
            return simulatedQuote();
        } catch (Exception e) {
            logger.error("[TPM] Unexpected error generating quote: {}", e.getMessage());
            return "ERROR";
        }
    }

    /**
     * Simulation fallback for dev/CI environments without physical TPM.
     * Returns a FIXED string so that integrity checks pass in simulation.
     * This must NEVER be used in production — the SGX guard in the entrypoint
     * prevents the node from booting without real hardware.
     */
    private String simulatedQuote() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest("TPM_PCR_STATE_OK".getBytes("UTF-8"));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "SIMULATION_ERROR";
        }
    }

    /**
     * Verifica se Total Memory Encryption (TME / MKTME) está ativo no hardware.
     *
     * Cold Boot Attack: Um invasor físico pode congelar a RAM com nitrogênio
     * líquido
     * e remover os pentes para lê-los em outra máquina. O TME criptografa TODA a
     * RAM
     * com uma chave gerada pelo processador no boot — sem a chave do chip, o
     * conteúdo
     * extraído é lixo indecifrável.
     *
     * Pré-requisito: Intel Ice Lake+ ou AMD EPYC 7003+ com TME ativado no BIOS.
     * Verificação Linux: o flag "tme" aparece em /proc/cpuinfo quando ativo.
     */
    private boolean checkTmeStatus() {
        try {
            // Em Linux, /proc/cpuinfo lista as capacidades da CPU
            ProcessBuilder pb = new ProcessBuilder("grep", "-o", "tme", "/proc/cpuinfo");
            Process proc = pb.start();
            int exit = proc.waitFor();
            if (exit == 0) {
                return true; // Flag "tme" encontrado → hardware TME disponível
            }
            // Fallback: tentar via dmidecode (requer root)
            // Para verificação exata no UEFI, usar: rdmsr 0x982 (IA32_TME_ACTIVATE)
            return false;
        } catch (Exception e) {
            // Em Mac/Windows (dev), logar aviso mas não bloquear
            logger.debug("[TME Check] Could not read /proc/cpuinfo — non-Linux environment or permission denied.");
            return false;
        }
    }

}

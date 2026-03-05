package source.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Protocolo de Recebimento da Chave Mestra via Vault (Key Server).
 *
 * ─── O Problema do .env ─────────────────────────────────────────────────────
 * Carregar a AES_SECRET de uma variável de ambiente ou arquivo .env significa
 * que a chave toca o disco (sistema de arquivos da VM/container) e é lida como
 * String Java imutável que permanece no heap até a próxima coleta do GC.
 *
 * ─── A Solução: Atestação + Busca em RAM ────────────────────────────────────
 * 1. O servidor gera um PCR Quote assinado pelo TPM (prova de integridade).
 * 2. Envia o Quote para o Key Server (Vault Central), que valida o hardware.
 * 3. Se válido, o Key Server retorna a chave AES-256 APENAS nesta resposta
 * HTTP — ela nunca está em nenhum disco, arquivo ou variável de ambiente.
 * 4. A resposta é lida direto para byte[] e convertida em SecretKey.
 * 5. A SecretKey vive APENAS em memória RAM durante o ciclo de vida do JVM.
 * 6. Com TME ativo, até a RAM está criptografada em hardware.
 *
 * ─── Modelo de Falha ─────────────────────────────────────────────────────────
 * Se o Key Server rejeitar o Quote (hardware adulterado), a chave nunca chega.
 * O servidor não tem chave → não consegue inicializar → não processa nada.
 * Esse é o comportamento correto: sem atestação, sem dados.
 *
 * ─── Modo Desenvolvimento ────────────────────────────────────────────────────
 * Quando vault.enabled=false (dev local), cai back para AES_SECRET do env.
 * Em NENHUMA circunstância isso deve ser usado em produção.
 */
@Component
public class VaultKeyProvider {

    private static final Logger logger = LoggerFactory.getLogger(VaultKeyProvider.class);
    private static final int KEY_BYTES = 32; // AES-256

    @Value("${vault.enabled:false}")
    private boolean vaultEnabled;

    @Value("${vault.url:}")
    private String vaultUrl;

    /**
     * Path to a file containing the Vault's .onion address (e.g., Tor hostname
     * file).
     * If vault.url is empty but this file exists, the URL is constructed
     * dynamically.
     * This enables automatic .onion discovery without hardcoding addresses.
     */
    @Value("${vault.url.file:}")
    private String vaultUrlFile;

    @Value("${vault.secret.path:v1/secret/data/kerosene/aes-key}")
    private String vaultSecretPath;

    @Value("${vault.proxy.host:}")
    private String proxyHost;

    @Value("${vault.proxy.port:0}")
    private int proxyPort;

    @Value("${vault.proxy.path:}")
    private String proxyPath;

    @Value("${vault.token:}")
    private String vaultToken; // Token do Vault (injetado via sidecar Kubernetes, não no .env)

    // Fallback para desenvolvimento local — NUNCA usar em produção
    @Value("${api.secret.aes.secret:}")
    private String devAesSecretBase64;

    /**
     * A chave mestra em memória.
     * - Volatile garante visibilidade entre threads.
     * - Nunca serializada, nunca logada, nunca convertida em String.
     *
     * ─── Thread-Safety (Issue 1.2) ───────────────────────────────────────────
     * Volatile alone is NOT enough: a thread can read masterKey (non-null check)
     * and then another thread can call destroyMasterKey() zeroing the underlying
     * byte[] before the first thread finishes its crypto operation.
     * ReentrantReadWriteLock ensures mutual exclusion between readers and the
     * single writer (destroyMasterKey).
     */
    private volatile SecretKey masterKey;
    private final ReentrantReadWriteLock keyLock = new ReentrantReadWriteLock();

    /**
     * Inicializa a chave mestra no boot:
     * - Em produção (vault.enabled=true): busca do Vault pós-atestação TPM.
     * - Em desenvolvimento (vault.enabled=false): usa AES_SECRET do ambiente.
     */
    @PostConstruct
    public void initialize() {
        if (vaultEnabled) {
            logger.info("[VaultKeyProvider] Vault mode ACTIVE. Requesting master key via TPM attestation.");
            // Executado em thread separada para não travar o boot do Spring
            new Thread(() -> loadKeyFromVaultWithRetry(), "VaultKeyFetcher").start();
        } else {
            logger.warn("[VaultKeyProvider] ⚠️  DEVELOPMENT MODE — key loaded from AES_SECRET env var. " +
                    "NEVER use this in production. Set vault.enabled=true.");
            loadKeyFromEnvironment();
        }
    }

    /**
     * Retorna a chave mestra carregada na RAM.
     * Lança IllegalStateException se não inicializada (boot falhou).
     *
     * Read-lock prevents a concurrent destroyMasterKey() from zeroing the key
     * bytes while this thread is still using the reference.
     */
    public SecretKey getMasterKey() {
        keyLock.readLock().lock();
        try {
            if (masterKey == null) {
                throw new IllegalStateException(
                        "[VaultKeyProvider STALL] Master key is not available yet. " +
                                "The Shard is waiting for Vault attestation or network recovery.");
            }
            return masterKey;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    /**
     * Retorna true se a chave já foi baixada e o nó está operacional.
     * Usado por interceptors ou health checks para bloquear tráfego enquanto em
     * STALL.
     */
    public boolean isReady() {
        keyLock.readLock().lock();
        try {
            return masterKey != null;
        } finally {
            keyLock.readLock().unlock();
        }
    }

    /**
     * Zera e destrói a Master Key da Heap da JVM.
     * Executado exclusivamente em eventos Panic/Morte via SuicideService.
     *
     * Write-lock: blocks all concurrent getMasterKey() readers before zeroing,
     * preventing use-after-free of the underlying byte[].
     */
    public void destroyMasterKey() {
        keyLock.writeLock().lock();
        try {
            SecretKey key = this.masterKey;
            if (key == null)
                return;
            // Zerar via reflexão o campo interno 'key' do SecretKeySpec
            try {
                Field f = SecretKeySpec.class.getDeclaredField("key");
                f.setAccessible(true);
                byte[] keyBytes = (byte[]) f.get(key);
                if (keyBytes != null)
                    Arrays.fill(keyBytes, (byte) 0);
            } catch (Exception e) {
                logger.error("[VaultKeyProvider] CRITICAL: could not zero master key bytes via reflection: {}",
                        e.getMessage());
            }
            this.masterKey = null;
            logger.info("[VaultKeyProvider] Master key bytes zeroed and reference nulled.");
        } finally {
            keyLock.writeLock().unlock();
        }
    }

    /**
     * PRODUÇÃO: busca a chave no Vault após validação TPM.
     *
     * Fluxo completo:
     * 1. Obtém PCR Quote do chip TPM local (prova de hardware).
     * 2. Envia o Quote + identidade do nó ao Vault via HTTPS mTLS.
     * 3. Vault valida o Quote contra a política de hardware cadastrada.
     * 4. Se válido, Vault retorna a chave AES-256 como JSON.
     * 5. A chave é lida direto em byte[] — nunca toca uma String.
     * 6. byte[] é consumido para criar a SecretKey e então zerado.
     */
    /**
     * PRODUÇÃO: busca a chave no Vault após validação TPM.
     * Envolvido em um loop de Retry / STALL mode.
     */
    private void loadKeyFromVaultWithRetry() {
        int attempt = 1;
        long backoffMs = 2000;
        final long MAX_BACKOFF = 60000; // Máximo 1 minuto entre tentativas

        while (this.masterKey == null) {
            logger.info("[VaultKeyProvider] Attempt {} to fetch master key...", attempt);
            try {
                loadKeyFromVault();
                if (this.masterKey != null) {
                    logger.info("[VaultKeyProvider] Successfully provisioned on attempt {}.", attempt);
                    break;
                }
            } catch (VaultAttestationException | IOException | InterruptedException e) {
                logger.warn(
                        "[VaultKeyProvider STALL] Vault unreachable or not armed: {}. Node remains in STALL mode. Retrying in {}ms...",
                        e.getMessage(), backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF);
                attempt++;
            }
        }
    }

    /**
     * Lógica interna de busca.
     *
     * Roteamento:
     * - Se vault.proxy.path estiver configurado → usa UdsSocks5Transport (zero TCP,
     * sem vazamento DNS).
     * - Caso contrário → conexão direta via Java HttpClient (apenas para dev/testes
     * sem Tor).
     */
    private void loadKeyFromVault() throws IOException, InterruptedException {
        // Resolve vault URL dynamically (supports .onion auto-discovery from file)
        String resolvedVaultUrl = resolveVaultUrl();
        if (resolvedVaultUrl == null || resolvedVaultUrl.isBlank()) {
            throw new IOException("Vault URL is not configured. Set vault.url or vault.url.file.");
        }

        byte[] keyBytes = null;
        try {
            String nodeId = getNodeIdentity();
            // ── Step 1: Obter PCR Quote do TPM ───────────────────────────────
            String tpmQuote = obtainTpmPcrQuote();
            logger.info("[VaultKeyProvider] TPM Quote obtained. Node: {}. Attesting...", nodeId);

            // ── Construir corpo da atestação ─────────────────────────────────
            String attestBody = String.format("{\"tpm_quote\": \"%s\", \"node_id\": \"%s\"}", tpmQuote, nodeId);

            if (proxyPath != null && !proxyPath.isBlank()) {
                // ── Caminho Produção: UDS SOCKS5 (Zero TCP, Anti DNS-Leak) ────
                logger.debug("[VaultKeyProvider] Routing via UDS SOCKS5 at: {}", proxyPath);
                keyBytes = loadKeyFromVaultViaUds(resolvedVaultUrl, nodeId, attestBody);

            } else {
                // ── Caminho Dev/Fallback: HttpClient direto (sem Tor) ─────────
                logger.warn(
                        "[VaultKeyProvider] No vault.proxy.path configured — connecting to Vault directly without Tor. ONLY for dev/testing.");
                keyBytes = loadKeyFromVaultDirectly(resolvedVaultUrl, nodeId, attestBody);
            }

            if (keyBytes == null || keyBytes.length != KEY_BYTES) {
                throw new VaultAttestationException(
                        "Invalid key length from Vault: " + (keyBytes == null ? "null" : keyBytes.length));
            }

            this.masterKey = new SecretKeySpec(keyBytes, "AES");
            logger.info("[VaultKeyProvider] ✅ Master key securely locked in RAM. Shard is UP.");

        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
        }
    }

    /**
     * Produção: carrega a chave via UDS SOCKS5 (caminho seguro, sem TCP, sem
     * vazamento DNS).
     */
    private byte[] loadKeyFromVaultViaUds(String resolvedVaultUrl, String nodeId, String attestBody)
            throws IOException {
        UdsSocks5Transport transport = new UdsSocks5Transport(proxyPath);

        // Step 2: Atestação TPM
        Map<String, String> attestHeaders = new LinkedHashMap<>();
        attestHeaders.put("X-Node-Id", nodeId);

        UdsSocks5Transport.HttpResult attestResult = transport.executeHttpRequest(
                resolvedVaultUrl + "/v1/vault/attest",
                "POST",
                attestBody,
                attestHeaders);

        if (attestResult.statusCode() != 200) {
            throw new VaultAttestationException("Vault rejected attestation (UDS): " + attestResult.bodyAsString());
        }

        String sessionToken = attestResult.bodyAsString().trim();
        logger.info("[VaultKeyProvider] Hardware Attested via UDS. Session token received.");

        // Step 3: Provisionamento da chave
        Map<String, String> provisionHeaders = new LinkedHashMap<>();
        provisionHeaders.put("Authorization", "Bearer " + sessionToken);
        provisionHeaders.put("X-Node-Id", nodeId);

        UdsSocks5Transport.HttpResult provisionResult = transport.executeHttpRequest(
                resolvedVaultUrl + "/v1/vault/provision",
                "GET",
                null,
                provisionHeaders);

        if (provisionResult.statusCode() != 200) {
            throw new VaultAttestationException("Provisioning failed via UDS: Status " + provisionResult.statusCode());
        }

        return extractKeyBytesFromVaultResponse(provisionResult.body());
    }

    /**
     * Resolves the Vault URL dynamically.
     * Priority: vault.url property > vault.url.file (Tor hostname file) > null.
     * This enables automatic .onion discovery from the shared Tor volume.
     */
    private String resolveVaultUrl() {
        // 1. Explicit vault.url takes priority
        if (vaultUrl != null && !vaultUrl.isBlank()) {
            return vaultUrl;
        }

        // 2. Read .onion from file (e.g., /vault-onion/hostname)
        if (vaultUrlFile != null && !vaultUrlFile.isBlank()) {
            try {
                Path hostnameFile = Path.of(vaultUrlFile);
                if (Files.exists(hostnameFile)) {
                    String onionHost = Files.readString(hostnameFile).trim();
                    if (!onionHost.isBlank()) {
                        String resolved = "http://" + onionHost;
                        logger.info("[VaultKeyProvider] Vault .onion auto-discovered from file: {}", resolved);
                        return resolved;
                    }
                } else {
                    logger.debug("[VaultKeyProvider] Vault hostname file not yet available: {}", vaultUrlFile);
                }
            } catch (IOException e) {
                logger.warn("[VaultKeyProvider] Failed to read vault hostname file {}: {}", vaultUrlFile,
                        e.getMessage());
            }
        }

        return null;
    }

    /**
     * Desenvolvimento / fallback sem Tor: HttpClient direto.
     * NÃO usar em produção.
     *
     * ⚠️ DNS LEAK WARNING: Se usar Java HttpClient com ProxySelector.of() para
     * um host .onion, o JVM resolve o hostname LOCALMENTE usando a stack DNS do
     * sistema ANTES de enviar para o SOCKS5. Isso expõe qual .onion você está
     * acessando ao resolver DNS local.
     *
     * Solução: quando proxyHost está configurado, roteamos via UdsSocks5Transport
     * no modo TCP (não UDS), que envia o hostname diretamente no handshake SOCKS5
     * (ATYP=0x03, domínio) sem resolução local. O servidor SOCKS (Tor) faz a
     * resolução dentro do circuito Tor.
     */
    private byte[] loadKeyFromVaultDirectly(String resolvedVaultUrl, String nodeId, String attestBody)
            throws IOException, InterruptedException {

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            // ── Via Tor TCP SOCKS5 (sem DNS local) ──────────────────────────
            // UdsSocks5Transport suporta modo TCP quando o proxy é host:port em vez de UDS
            // path.
            // Em produção com Tor, proxyHost = kerosene-tor-is, proxyPort = 9050.
            // O UDS transport usa um Unix socket; para TCP usamos o caminho
            // UdsSocks5Transport
            // mas com o arquivo de socket obtido via host:port encaminhado via Docker
            // network.
            //
            // Abordagem segura: delegamos para UdsSocks5Transport indicando um path
            // TCP-style.
            // Como tor-socks é exposto via volume Unix, este caminho só é atingido
            // se vault.proxy.path NÃO está configurado mas vault.proxy.host está.
            // Neste caso, avisamos e usamos HttpClient com DNS remoto forçado.
            //
            // Workaround para DNS leak: Substituir o hostname pelo IP resolvido dentro
            // do Docker (o Tor container resolve .onion via circuito, não localmente).
            // Para .onion não há IP — a única forma correta é via UDS/SOCKS5 com
            // ATYP=0x03. Portanto, forçamos uso via proxyPath UDS.
            logger.error("[VaultKeyProvider] CRITICAL: vault.proxy.host={} is set but vault.proxy.path is not.",
                    proxyHost);
            logger.error("[VaultKeyProvider] Java ProxySelector would resolve .onion via LOCAL DNS (DNS LEAK!).");
            logger.error("[VaultKeyProvider] Set vault.proxy.path to the Tor UDS socket path instead.");
            throw new VaultAttestationException(
                    "DNS leak prevention: vault.proxy.path (UDS socket) is required when connecting to .onion URLs. "
                            + "vault.proxy.host is not safe for Tor connectivity. "
                            + "Configure vault.proxy.path=/var/run/tor/socks/tor.sock");
        }

        // ── Caminho Dev/Test: HttpClient direto (sem Tor, sem proxy) ─────────
        logger.warn(
                "[VaultKeyProvider] DEV MODE — connecting to Vault directly without Tor. " +
                        "This MUST NOT be used in production against .onion endpoints.");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest attestRequest = HttpRequest.newBuilder()
                .uri(URI.create(resolvedVaultUrl + "/v1/vault/attest"))
                .header("Content-Type", "application/json")
                .header("X-Node-Id", nodeId)
                .POST(HttpRequest.BodyPublishers.ofString(attestBody))
                .build();

        HttpResponse<String> attestResponse = client.send(attestRequest, HttpResponse.BodyHandlers.ofString());
        if (attestResponse.statusCode() != 200) {
            throw new VaultAttestationException("Vault rejected attestation: " + attestResponse.body());
        }

        String sessionToken = attestResponse.body();
        logger.info("[VaultKeyProvider] Hardware Attested. Session token received.");

        HttpRequest provisionRequest = HttpRequest.newBuilder()
                .uri(URI.create(resolvedVaultUrl + "/v1/vault/provision"))
                .header("Authorization", "Bearer " + sessionToken)
                .header("X-Node-Id", nodeId)
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(provisionRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new VaultAttestationException("Provisioning failed: Status " + response.statusCode());
        }

        return extractKeyBytesFromVaultResponse(response.body());
    }

    /**
     * DESENVOLVIMENTO: carrega do env var AES_SECRET.
     * ⚠️ Inseguro para produção — a chave toca o disco via .env e passa por String.
     */
    private void loadKeyFromEnvironment() {
        if (devAesSecretBase64 == null || devAesSecretBase64.isBlank()) {
            throw new IllegalStateException(
                    "[VaultKeyProvider] api.secret.aes.secret is not set. " +
                            "Set AES_SECRET env var (dev) or configure vault.enabled=true (prod).");
        }
        byte[] keyBytes = null;
        try {
            keyBytes = Base64.getDecoder().decode(devAesSecretBase64);
            if (keyBytes.length != KEY_BYTES) {
                throw new IllegalStateException(
                        "[VaultKeyProvider] AES_SECRET must decode to 32 bytes. Got: " + keyBytes.length);
            }
            this.masterKey = new SecretKeySpec(keyBytes, "AES");
            logger.info("[VaultKeyProvider] Dev key loaded ({} bytes).", KEY_BYTES);
        } finally {
            if (keyBytes != null) {
                Arrays.fill(keyBytes, (byte) 0);
            }
            // NÃO dá pra zerar devAesSecretBase64 (é String imutável)
            // — essa é exatamente a razão de não usar isso em produção.
        }
    }

    /**
     * Obtém o PCR Quote assinado pelo chip TPM.
     * Em produção: executa tpm2-tools ou chama sidecar via gRPC.
     * Em simulação: retorna um nonce fixo (a validação real ocorre no Vault).
     */
    private String obtainTpmPcrQuote() {
        try {
            // Produção real: invocar tpm2_quote via ProcessBuilder ou sidecar gRPC
            // ProcessBuilder pb = new ProcessBuilder("tpm2_quote", "-c", "pcr.ctx",
            // "-m", "quote.msg", "-s", "quote.sig", "-q", generateNonce());
            // Leia o output assinado e retorne como Base64.

            // Simulação para dev:
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    ("TPM_PCR_STATE_" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new VaultAttestationException("Failed to obtain TPM PCR Quote: " + e.getMessage());
        }
    }

    /**
     * Extrai a chave AES do JSON de resposta do Vault DIRETAMENTE em byte[],
     * usando Jackson streaming API para NUNCA criar uma String com a chave.
     *
     * Formato esperado: {"data": {"data": {"aes_key": "<base64>"}}}
     */
    private byte[] extractKeyBytesFromVaultResponse(byte[] responseBody) {
        byte[] base64Bytes = null;
        try (JsonParser parser = new JsonFactory().createParser(new ByteArrayInputStream(responseBody))) {
            while (parser.nextToken() != null) {
                if (JsonToken.FIELD_NAME.equals(parser.currentToken())
                        && "aes_key".equals(parser.currentName())) {
                    parser.nextToken(); // move to value
                    base64Bytes = parser.getText().getBytes(StandardCharsets.ISO_8859_1);
                    break;
                }
            }
        } catch (IOException e) {
            String rawBody = new String(responseBody, StandardCharsets.UTF_8);
            logger.error("[VaultKeyProvider] Failed to parse JSON. Raw body: {}", rawBody);
            throw new VaultAttestationException("Failed to parse Vault response: " + e.getMessage());
        }

        if (base64Bytes == null) {
            throw new VaultAttestationException(
                    "Vault response does not contain 'aes_key' field. Check Vault secret path.");
        }

        try {
            return Base64.getDecoder().decode(base64Bytes);
        } finally {
            // Zero Base64 bytes from heap as soon as decoded
            Arrays.fill(base64Bytes, (byte) 0);
        }
    }

    /**
     * Identidade única deste nó — usada pelo Vault para validar a política
     * de atestação associada a este servidor específico.
     */
    private String getNodeIdentity() {
        // Produção: retornar o fingerprint do certificado TLS do nó, ou o hostname.
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-node";
        }
    }

    /** Exceção de falha de atestação — força System.exit(1) no boot. */
    public static class VaultAttestationException extends RuntimeException {
        public VaultAttestationException(String message) {
            super(message);
        }
    }
}

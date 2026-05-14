package source.treasury.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.treasury.application.port.out.MerkleLedgerPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 🌳 MERKLE TREE LEDGER (Solvência e Integridade)
 * ─────────────────────────────────────────────────────────────
 * Em vez de validar o HMAC linha por linha (O(n) CPU Burn), mantemos uma
 * Merkle Root acumulada. Cada nova transação é um nó folha. Isso permite
 * auditar milhões de registros validando apenas o "Top Hash".
 */
@Service
public class MerkleLedgerService implements MerkleLedgerPort {

    private static final Logger log = LoggerFactory.getLogger(MerkleLedgerService.class);

    // O Merkle Root atual do sistema. Deve ser persistido no fim do bloco/dia.
    private final AtomicReference<String> currentRoot = new AtomicReference<>("");

    public MerkleLedgerService() {
        // Em produção, isso seria carregado do banco (último estado conhecido)
        this.currentRoot.set("0000000000000000000000000000000000000000000000000000000000000000");
    }

    /**
     * Adiciona uma nova folha ao Ledger e recalcula a raiz acumulativa.
     *
     * @param entryData Dados concatenados da linha (ID + Amount + Timestamp)
     * @return O novo Merkle Root
     */
    @Override
    public String appendEntry(String entryData) {
        String prevRoot = currentRoot.get();
        String newLeaf = sha256(entryData);

        // Combinação cumulativa: NewRoot = SHA256(PrevRoot + NewLeaf)
        String newRoot = sha256(prevRoot + newLeaf);

        currentRoot.set(newRoot);
        log.debug("[MerkleLedger] Root updated: {} -> {}", prevRoot.substring(0, 8), newRoot.substring(0, 8));

        return newRoot;
    }

    @Override
    public String getCurrentRoot() {
        return currentRoot.get();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

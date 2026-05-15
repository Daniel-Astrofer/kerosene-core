package source.ledger.audit;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.entity.LedgerEntity;
import source.ledger.repository.LedgerRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Computes and persists a SHA-256 Merkle root over all internal ledger
 * balances.
 *
 * The Merkle construction is:
 * leaf = SHA256( walletId + "|" + balance )
 * parent = SHA256( leftChildHash + rightChildHash )
 *
 * Leaves are sorted deterministically by walletId so the root is reproducible.
 * The root proves that no balance was silently altered without producing a
 * different root — without revealing which wallet owns which balance.
 */
@Service
public class MerkleAuditService {

    private final LedgerRepository ledgerRepository;
    private final MerkleAuditRepository auditRepository;

    public MerkleAuditService(LedgerRepository ledgerRepository,
            MerkleAuditRepository auditRepository) {
        this.ledgerRepository = ledgerRepository;
        this.auditRepository = auditRepository;
    }

    /**
     * Computes the Merkle root and persists a new audit checkpoint.
     *
     * @return the persisted {@link MerkleAuditEntity}
     */
    @Transactional
    public MerkleAuditEntity computeAndPersist() {
        List<LedgerEntity> ledgers = ledgerRepository.findAll();

        if (ledgers.isEmpty()) {
            // Nothing to audit yet; persist a sentinel root
            MerkleAuditEntity empty = new MerkleAuditEntity(sha256("EMPTY_LEDGER"), 0L);
            return auditRepository.save(empty);
        }

        // Sort deterministically so the tree is stable across nodes
        ledgers.sort(Comparator.comparingLong(l -> l.getWallet().getId()));

        List<String> leaves = ledgers.stream()
                .map(l -> sha256(l.getWallet().getId() + "|" + l.getBalance().toPlainString()))
                .toList();

        String merkleRoot = buildMerkleRoot(leaves);

        MerkleAuditEntity checkpoint = new MerkleAuditEntity(merkleRoot, (long) ledgers.size());
        return auditRepository.save(checkpoint);
    }

    /**
     * Returns the most recent audit checkpoint, if any.
     */
    public Optional<MerkleAuditEntity> findLatest() {
        return auditRepository.findTopByOrderByCreatedAtDesc();
    }

    /**
     * Returns the last {@code limit} audit checkpoints, newest first.
     */
    public List<MerkleAuditEntity> findHistory(int limit) {
        return auditRepository.findLatestCheckpoints(PageRequest.of(0, limit));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Merkle Tree
    // ──────────────────────────────────────────────────────────────────────────

    private String buildMerkleRoot(List<String> hashes) {
        if (hashes.size() == 1) {
            return hashes.get(0);
        }

        List<String> nextLevel = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i += 2) {
            String left = hashes.get(i);
            // If odd number of nodes, duplicate the last one (standard Bitcoin convention)
            String right = (i + 1 < hashes.size()) ? hashes.get(i + 1) : left;
            nextLevel.add(sha256(left + right));
        }
        return buildMerkleRoot(nextLevel);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SHA-256 helper
    // ──────────────────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Verifies that a given balance value for a walletId is included in a
     * known Merkle root. Useful for spot-checking without exposing all balances.
     *
     * @param walletId   the wallet to verify
     * @param balance    the claimed balance
     * @param merkleRoot the root to verify against
     * @param proof      ordered list of sibling hashes (Merkle proof path)
     * @return true if the leaf is provably included in the root
     */
    public boolean verifyMerkleProof(Long walletId, BigDecimal balance,
            String merkleRoot, List<String> proof) {
        String current = sha256(walletId + "|" + balance.toPlainString());
        for (String sibling : proof) {
            // Concatenate alphabetically to match the construction order
            if (current.compareTo(sibling) <= 0) {
                current = sha256(current + sibling);
            } else {
                current = sha256(sibling + current);
            }
        }
        return current.equals(merkleRoot);
    }
}

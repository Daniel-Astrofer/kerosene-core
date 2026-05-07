package source.treasury.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record OperationalReserveProofResponseDTO(
        Instant generatedAt,
        String status,
        boolean solvent,
        boolean providersHealthy,
        Assets assets,
        Liabilities liabilities,
        ChainState chainState,
        MerkleProof merkleProof,
        List<ProviderHealth> providers,
        String snapshotHash,
        String panicReason) {

    public record Assets(
            BigDecimal hotWalletBtc,
            BigDecimal treasuryXpubOnchainBtc,
            BigDecimal lightningBtc,
            BigDecimal totalOnchainBtc,
            BigDecimal totalAssetsBtc) {
    }

    public record Liabilities(
            BigDecimal internalLedgerBtc,
            BigDecimal reservedOnchainBtc,
            BigDecimal reservedLightningBtc,
            BigDecimal totalOperationalExposureBtc) {
    }

    public record ChainState(
            String bitcoinNetwork,
            Long bitcoinBlockHeight,
            String bitcoinBestBlockHashRef,
            Long lightningBlockHeight,
            String lightningBlockHashRef) {
    }

    public record MerkleProof(
            String merkleRoot,
            Long ledgerCount,
            LocalDateTime createdAt,
            String anchorTxidRef) {
    }

    public record ProviderHealth(
            String provider,
            String status,
            String source,
            String message) {
    }
}

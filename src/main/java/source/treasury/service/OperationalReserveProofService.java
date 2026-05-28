package source.treasury.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.infra.logging.LogSanitizer;
import source.common.observability.FinancialOperationsMetrics;
import source.ledger.audit.MerkleAuditEntity;
import source.ledger.audit.MerkleAuditService;
import source.transactions.monitoring.BitcoinBlockchainMonitorService;
import source.transactions.monitoring.LightningNetworkMonitorService;
import source.transactions.repository.ExternalTransferRepository;
import source.treasury.application.port.in.PerformFinancialAuditUseCase;
import source.treasury.domain.model.FinancialAuditResult;
import source.treasury.domain.model.ReserveSnapshot;
import source.treasury.dto.OperationalReserveProofResponseDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationalReserveProofService {

    private static final List<String> RESERVED_STATUSES = List.of("PENDING", "MEMPOOL", "CONFIRMED");

    private final PerformFinancialAuditUseCase financialAuditUseCase;
    private final MerkleAuditService merkleAuditService;
    private final BitcoinBlockchainMonitorService bitcoinMonitorService;
    private final LightningNetworkMonitorService lightningMonitorService;
    private final ExternalTransferRepository externalTransferRepository;
    private final FinancialAuditTrailService auditTrailService;
    private final FinancialOperationsMetrics metrics;

    public OperationalReserveProofService(
            PerformFinancialAuditUseCase financialAuditUseCase,
            MerkleAuditService merkleAuditService,
            BitcoinBlockchainMonitorService bitcoinMonitorService,
            LightningNetworkMonitorService lightningMonitorService,
            ExternalTransferRepository externalTransferRepository,
            FinancialAuditTrailService auditTrailService,
            FinancialOperationsMetrics metrics) {
        this.financialAuditUseCase = financialAuditUseCase;
        this.merkleAuditService = merkleAuditService;
        this.bitcoinMonitorService = bitcoinMonitorService;
        this.lightningMonitorService = lightningMonitorService;
        this.externalTransferRepository = externalTransferRepository;
        this.auditTrailService = auditTrailService;
        this.metrics = metrics;
    }

    @Transactional
    public OperationalReserveProofResponseDTO generateSnapshot() {
        Instant generatedAt = Instant.now();
        FinancialAuditResult auditResult = financialAuditUseCase.performAudit();
        ReserveSnapshot reserves = auditResult.reserveSnapshot() != null
                ? auditResult.reserveSnapshot()
                : emptyReserves();
        MerkleAuditEntity merkle = merkleAuditService.computeAndPersist();
        BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot bitcoin = bitcoinMonitorService.snapshot();
        LightningNetworkMonitorService.LightningMonitorSnapshot lightning = lightningMonitorService.snapshot();

        BigDecimal ledgerLiabilities = normalize(auditResult.totalLiabilitiesBtc());
        BigDecimal reservedOnchain = normalize(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(
                "ONCHAIN",
                RESERVED_STATUSES));
        BigDecimal reservedLightning = normalize(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(
                "LIGHTNING",
                RESERVED_STATUSES));

        boolean providersHealthy = !"DOWN".equalsIgnoreCase(bitcoin.status())
                && !"DOWN".equalsIgnoreCase(lightning.status());
        boolean providersDegraded = "DEGRADED".equalsIgnoreCase(bitcoin.status())
                || "DEGRADED".equalsIgnoreCase(lightning.status());
        boolean solvent = auditResult.executed() && auditResult.solvent();
        String status = resolveStatus(auditResult, providersHealthy, providersDegraded);

        OperationalReserveProofResponseDTO.Assets assets = new OperationalReserveProofResponseDTO.Assets(
                normalize(reserves.hotWalletBtc()),
                normalize(reserves.treasuryXpubOnchainBtc()),
                normalize(reserves.lightningBtc()),
                normalize(reserves.totalOnchainBtc()),
                normalize(reserves.totalAssetsBtc()));
        OperationalReserveProofResponseDTO.Liabilities liabilities = new OperationalReserveProofResponseDTO.Liabilities(
                ledgerLiabilities,
                reservedOnchain,
                reservedLightning,
                ledgerLiabilities.add(reservedOnchain).add(reservedLightning).setScale(8, RoundingMode.HALF_UP));
        OperationalReserveProofResponseDTO.ChainState chainState = new OperationalReserveProofResponseDTO.ChainState(
                bitcoin.network(),
                longValue(bitcoin.chain(), "height"),
                LogSanitizer.fingerprint(textValue(bitcoin.chain(), "bestBlockHash")),
                longValue(lightning.node(), "blockHeight"),
                LogSanitizer.fingerprint(textValue(lightning.node(), "blockHash")));
        OperationalReserveProofResponseDTO.MerkleProof merkleProof = new OperationalReserveProofResponseDTO.MerkleProof(
                merkle.getMerkleRoot(),
                merkle.getLedgerCount(),
                merkle.getCreatedAt(),
                LogSanitizer.fingerprint(merkle.getAnchorTxid()));
        List<OperationalReserveProofResponseDTO.ProviderHealth> providers = List.of(
                new OperationalReserveProofResponseDTO.ProviderHealth(
                        "BITCOIN",
                        bitcoin.status(),
                        bitcoin.primarySource(),
                        bitcoin.message()),
                new OperationalReserveProofResponseDTO.ProviderHealth(
                        "LIGHTNING",
                        lightning.status(),
                        lightning.primarySource(),
                        lightning.message()));
        String snapshotHash = snapshotHash(status, assets, liabilities, chainState, merkleProof, generatedAt);

        OperationalReserveProofResponseDTO response = new OperationalReserveProofResponseDTO(
                generatedAt,
                status,
                solvent,
                providersHealthy,
                assets,
                liabilities,
                chainState,
                merkleProof,
                providers,
                snapshotHash,
                auditResult.panicReason());

        auditTrailService.recordBestEffort(
                "OPERATIONAL_RESERVE_PROOF_GENERATED",
                "OPERATIONAL_RESERVE_PROOF",
                snapshotHash,
                null,
                snapshotHash,
                auditPayload(response));
        metrics.increment("operational_reserve_proof", status);
        return response;
    }

    private String resolveStatus(
            FinancialAuditResult auditResult,
            boolean providersHealthy,
            boolean providersDegraded) {
        if (!auditResult.executed()) {
            return "SKIPPED";
        }
        if (!auditResult.solvent()) {
            return "INSOLVENT";
        }
        if (!providersHealthy) {
            return "PROVIDER_DOWN";
        }
        if (providersDegraded) {
            return "DEGRADED";
        }
        return "SOLVENT";
    }

    private Map<String, Object> auditPayload(OperationalReserveProofResponseDTO response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", response.status());
        payload.put("solvent", response.solvent());
        payload.put("providersHealthy", response.providersHealthy());
        payload.put("totalAssetsBtc", response.assets().totalAssetsBtc().toPlainString());
        payload.put("totalLiabilitiesBtc", response.liabilities().internalLedgerBtc().toPlainString());
        payload.put("snapshotHash", response.snapshotHash());
        payload.put("merkleRoot", response.merkleProof().merkleRoot());
        payload.put("bitcoinBlockHeight", response.chainState().bitcoinBlockHeight() != null
                ? response.chainState().bitcoinBlockHeight()
                : 0L);
        payload.put("lightningBlockHeight", response.chainState().lightningBlockHeight() != null
                ? response.chainState().lightningBlockHeight()
                : 0L);
        return payload;
    }

    private ReserveSnapshot emptyReserves() {
        BigDecimal zero = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        return new ReserveSnapshot(zero, zero, zero, zero, zero, zero);
    }

    private BigDecimal normalize(BigDecimal value) {
        return value != null ? value.setScale(8, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
    }

    private Long longValue(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private String textValue(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return value != null ? value.toString() : null;
    }

    private String snapshotHash(
            String status,
            OperationalReserveProofResponseDTO.Assets assets,
            OperationalReserveProofResponseDTO.Liabilities liabilities,
            OperationalReserveProofResponseDTO.ChainState chainState,
            OperationalReserveProofResponseDTO.MerkleProof merkleProof,
            Instant generatedAt) {
        String material = status
                + "|" + generatedAt
                + "|" + assets.totalAssetsBtc().toPlainString()
                + "|" + liabilities.internalLedgerBtc().toPlainString()
                + "|" + liabilities.totalOperationalExposureBtc().toPlainString()
                + "|" + chainState.bitcoinBlockHeight()
                + "|" + chainState.lightningBlockHeight()
                + "|" + merkleProof.merkleRoot()
                + "|" + merkleProof.ledgerCount();
        return sha256(material);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash reserve proof snapshot", exception);
        }
    }
}

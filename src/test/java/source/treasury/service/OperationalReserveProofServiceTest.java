package source.treasury.service;

import org.junit.jupiter.api.Test;
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
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalReserveProofServiceTest {

    private final PerformFinancialAuditUseCase auditUseCase = mock(PerformFinancialAuditUseCase.class);
    private final MerkleAuditService merkleAuditService = mock(MerkleAuditService.class);
    private final BitcoinBlockchainMonitorService bitcoinMonitorService = mock(BitcoinBlockchainMonitorService.class);
    private final LightningNetworkMonitorService lightningMonitorService = mock(LightningNetworkMonitorService.class);
    private final ExternalTransferRepository externalTransferRepository = mock(ExternalTransferRepository.class);
    private final FinancialAuditTrailService auditTrailService = mock(FinancialAuditTrailService.class);
    private final FinancialOperationsMetrics metrics = mock(FinancialOperationsMetrics.class);
    private final OperationalReserveProofService service = new OperationalReserveProofService(
            auditUseCase,
            merkleAuditService,
            bitcoinMonitorService,
            lightningMonitorService,
            externalTransferRepository,
            auditTrailService,
            metrics);

    @Test
    void generatesSolventSnapshotWithSanitizedProviderState() {
        givenAudit(true, true, null);
        givenMerkle();
        when(bitcoinMonitorService.snapshot()).thenReturn(bitcoin("UP"));
        when(lightningMonitorService.snapshot()).thenReturn(lightning("UP"));
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("ONCHAIN"), anyCollection()))
                .thenReturn(new BigDecimal("0.01000000"));
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("LIGHTNING"), anyCollection()))
                .thenReturn(new BigDecimal("0.02000000"));

        OperationalReserveProofResponseDTO response = service.generateSnapshot();

        assertEquals("SOLVENT", response.status());
        assertTrue(response.solvent());
        assertTrue(response.providersHealthy());
        assertEquals(new BigDecimal("1.00000000"), response.assets().totalAssetsBtc());
        assertEquals(new BigDecimal("0.50000000"), response.liabilities().internalLedgerBtc());
        assertEquals(new BigDecimal("0.53000000"), response.liabilities().totalOperationalExposureBtc());
        assertEquals(840000L, response.chainState().bitcoinBlockHeight());
        assertTrue(response.chainState().bitcoinBestBlockHashRef().startsWith("sha256:"));
        assertEquals("a".repeat(64), response.merkleProof().merkleRoot());
        assertNotNull(response.snapshotHash());
        verify(auditTrailService).recordBestEffort(
                eq("OPERATIONAL_RESERVE_PROOF_GENERATED"),
                eq("OPERATIONAL_RESERVE_PROOF"),
                eq(response.snapshotHash()),
                org.mockito.ArgumentMatchers.isNull(),
                eq(response.snapshotHash()),
                anyMap());
        verify(metrics).increment("operational_reserve_proof", "SOLVENT");
    }

    @Test
    void marksSnapshotInsolventWhenAuditDetectsDeficit() {
        givenAudit(true, false, "INSOLVENCY_DETECTED");
        givenMerkle();
        when(bitcoinMonitorService.snapshot()).thenReturn(bitcoin("UP"));
        when(lightningMonitorService.snapshot()).thenReturn(lightning("UP"));
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("ONCHAIN"), anyCollection()))
                .thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("LIGHTNING"), anyCollection()))
                .thenReturn(BigDecimal.ZERO);

        OperationalReserveProofResponseDTO response = service.generateSnapshot();

        assertEquals("INSOLVENT", response.status());
        assertFalse(response.solvent());
        assertEquals("INSOLVENCY_DETECTED", response.panicReason());
    }

    @Test
    void marksSnapshotProviderDownWhenBitcoinProviderIsUnavailable() {
        givenAudit(true, true, null);
        givenMerkle();
        when(bitcoinMonitorService.snapshot()).thenReturn(bitcoin("DOWN"));
        when(lightningMonitorService.snapshot()).thenReturn(lightning("UP"));
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("ONCHAIN"), anyCollection()))
                .thenReturn(BigDecimal.ZERO);
        when(externalTransferRepository.sumReservedOutboundByNetworkAndStatuses(eq("LIGHTNING"), anyCollection()))
                .thenReturn(BigDecimal.ZERO);

        OperationalReserveProofResponseDTO response = service.generateSnapshot();

        assertEquals("PROVIDER_DOWN", response.status());
        assertFalse(response.providersHealthy());
        verify(metrics).increment("operational_reserve_proof", "PROVIDER_DOWN");
    }

    private void givenAudit(boolean executed, boolean solvent, String panicReason) {
        when(auditUseCase.performAudit()).thenReturn(new FinancialAuditResult(
                executed,
                solvent,
                new BigDecimal("0.50000000"),
                new ReserveSnapshot(
                        new BigDecimal("0.40000000"),
                        new BigDecimal("0.30000000"),
                        new BigDecimal("0.10000000"),
                        new BigDecimal("0.50000000"),
                        new BigDecimal("0.50000000"),
                        new BigDecimal("1.00000000")),
                panicReason));
    }

    private void givenMerkle() {
        when(merkleAuditService.computeAndPersist()).thenReturn(new MerkleAuditEntity("a".repeat(64), 42L));
    }

    private BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot bitcoin(String status) {
        return new BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot(
                status,
                "BITCOIN_PRUNED_NODE_RPC",
                "mainnet",
                "not-configured",
                false,
                Instant.now(),
                Map.of(
                        "height", 840000L,
                        "bestBlockHash", "000000000000000000000000000000000000000000000000000000000000000a"),
                Map.of(),
                java.util.List.of(),
                status.equals("UP") ? "Bitcoin pruned node is synced" : "Bitcoin Core RPC probe failed");
    }

    private LightningNetworkMonitorService.LightningMonitorSnapshot lightning(String status) {
        return new LightningNetworkMonitorService.LightningMonitorSnapshot(
                status,
                "LND_GRPC",
                Instant.now(),
                Map.of(
                        "blockHeight", 840000L,
                        "blockHash", "000000000000000000000000000000000000000000000000000000000000000b"),
                status.equals("UP") ? "LND is synced to chain" : "LND gRPC probe failed");
    }
}

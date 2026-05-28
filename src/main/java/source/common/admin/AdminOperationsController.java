package source.common.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.infra.health.OperationalHealthService;
import source.common.infra.health.OperationalHealthSnapshot;
import source.common.release.ReleaseManifestService;
import source.security.vault.VaultRaftHealthService;
import source.transactions.model.NetworkTransferEventEntity;
import source.transactions.monitoring.BitcoinBlockchainMonitorService;
import source.transactions.monitoring.LightningNetworkMonitorService;
import source.transactions.repository.ExternalTransferRepository;
import source.transactions.repository.NetworkTransferEventRepository;
import source.transactions.repository.PaymentLinkRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {

    private final OperationalHealthService operationalHealthService;
    private final BitcoinBlockchainMonitorService blockchainMonitorService;
    private final LightningNetworkMonitorService lightningNetworkMonitorService;
    private final VaultRaftHealthService vaultRaftHealthService;
    private final ReleaseManifestService releaseManifestService;
    private final MobileDownloadService mobileDownloadService;
    private final NetworkTransferEventRepository eventRepository;
    private final ExternalTransferRepository externalTransferRepository;
    private final PaymentLinkRepository paymentLinkRepository;

    public AdminOperationsController(
            OperationalHealthService operationalHealthService,
            BitcoinBlockchainMonitorService blockchainMonitorService,
            LightningNetworkMonitorService lightningNetworkMonitorService,
            VaultRaftHealthService vaultRaftHealthService,
            ReleaseManifestService releaseManifestService,
            MobileDownloadService mobileDownloadService,
            NetworkTransferEventRepository eventRepository,
            ExternalTransferRepository externalTransferRepository,
            PaymentLinkRepository paymentLinkRepository) {
        this.operationalHealthService = operationalHealthService;
        this.blockchainMonitorService = blockchainMonitorService;
        this.lightningNetworkMonitorService = lightningNetworkMonitorService;
        this.vaultRaftHealthService = vaultRaftHealthService;
        this.releaseManifestService = releaseManifestService;
        this.mobileDownloadService = mobileDownloadService;
        this.eventRepository = eventRepository;
        this.externalTransferRepository = externalTransferRepository;
        this.paymentLinkRepository = paymentLinkRepository;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", Instant.now());
        payload.put("health", operationalHealthService.dependencies());
        payload.put("blockchain", blockchainMonitorService.snapshot());
        payload.put("lightning", lightningNetworkMonitorService.snapshot());
        payload.put("vaultRaft", vaultRaftHealthService.snapshot());
        payload.put("release", releaseManifestService.snapshot());
        payload.put("mobile", mobileDownloadService.releaseInfo());
        return payload;
    }

    @GetMapping("/health")
    public OperationalHealthSnapshot health() {
        return operationalHealthService.dependencies();
    }

    @GetMapping("/blockchain")
    public BitcoinBlockchainMonitorService.BlockchainMonitorSnapshot blockchain() {
        return blockchainMonitorService.snapshot();
    }

    @GetMapping("/lightning")
    public LightningNetworkMonitorService.LightningMonitorSnapshot lightning() {
        return lightningNetworkMonitorService.snapshot();
    }

    @GetMapping("/vault-raft")
    public VaultRaftHealthService.VaultRaftSnapshot vaultRaft() {
        return vaultRaftHealthService.snapshot();
    }

    @GetMapping("/release")
    public ReleaseManifestService.ReleaseSnapshot release() {
        return releaseManifestService.snapshot();
    }

    @GetMapping("/mobile")
    public MobileDownloadService.MobileReleaseInfo mobile() {
        return mobileDownloadService.releaseInfo();
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return eventRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .limit(safeLimit)
                .map(this::toSafeLog)
                .toList();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", Instant.now());

        Map<String, Object> transfers = aggregateTransfers();
        Map<String, Object> paymentLinks = aggregatePaymentLinks();

        BigDecimal transferVolume = decimal(transfers.get("totalVolumeBtc"));
        BigDecimal linkVolume = decimal(paymentLinks.get("totalAmountBtc"));
        long transferCount = number(transfers.get("totalCount"));
        long linkCount = number(paymentLinks.get("linksCreated"));
        long totalEvents = transferCount + linkCount;

        BigDecimal totalVolume = transferVolume.add(linkVolume);
        BigDecimal totalFees = decimal(transfers.get("totalFeesBtc"));

        payload.put("totalVolumeBtc", totalVolume);
        payload.put("totalFeesBtc", totalFees);
        payload.put("totalTransactions", totalEvents);
        payload.put("avgTicketBtc", totalEvents > 0
                ? totalVolume.divide(BigDecimal.valueOf(totalEvents), 8, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        payload.put("confirmedTransactions",
                number(transfers.get("confirmedCount")) + number(paymentLinks.get("linksPaid")));
        payload.put("pendingTransactions",
                number(transfers.get("pendingCount")) + number(paymentLinks.get("linksPending")));
        payload.put("failedTransactions",
                number(transfers.get("failedCount")) + number(paymentLinks.get("linksExpired")));
        payload.put("transfers", transfers);
        payload.put("paymentLinks", paymentLinks);
        payload.put("privacyBoundary",
                "Aggregate operational metrics only; no user timeline, invoice payload, destination, txid, or wallet name.");
        return payload;
    }

    private Map<String, Object> toSafeLog(NetworkTransferEventEntity event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("createdAt", event.getCreatedAt());
        row.put("severity", event.getSeverity());
        row.put("eventType", event.getEventType());
        row.put("reference", fingerprint(event.getReference()));
        row.put("transferRef", fingerprint(event.getTransferId() != null ? event.getTransferId().toString() : null));
        row.put("userRef", fingerprint(event.getUserId() != null ? event.getUserId().toString() : null));
        row.put("payloadRef", fingerprint(redact(event.getPayload())));
        return row;
    }

    private String fingerprint(String value) {
        return source.common.infra.logging.LogSanitizer.fingerprint(value);
    }

    private String redact(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        String redacted = payload
                .replaceAll("(?i)(seed|private[_-]?key|token|secret|macaroon|password|passphrase)\\s*[:=]\\s*[^,}\\s]+", "$1=***")
                .replaceAll("(?i)(authorization|cookie)\\s*[:=]\\s*[^,}\\s]+", "$1=***");
        return redacted.length() > 512 ? redacted.substring(0, 512) : redacted;
    }

    private Map<String, Object> aggregateTransfers() {
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal inflow = BigDecimal.ZERO;
        BigDecimal outflow = BigDecimal.ZERO;
        long totalCount = 0;
        long onchainCount = 0;
        long lightningCount = 0;
        BigDecimal onchainVolume = BigDecimal.ZERO;
        BigDecimal lightningVolume = BigDecimal.ZERO;
        BigDecimal onchainFees = BigDecimal.ZERO;
        BigDecimal lightningFees = BigDecimal.ZERO;

        for (ExternalTransferRepository.NetworkAggregate row
                : externalTransferRepository.aggregateOperationalMetricsByNetwork()) {
            String network = normalize(row.getNetwork());
            long count = row.getEventCount();
            BigDecimal volume = nz(row.getVolumeBtc());
            BigDecimal fees = nz(row.getFeeBtc());
            totalCount += count;
            totalVolume = totalVolume.add(volume);
            totalFees = totalFees.add(fees);
            inflow = inflow.add(nz(row.getInflowBtc()));
            outflow = outflow.add(nz(row.getOutflowBtc()));
            if ("ONCHAIN".equals(network)) {
                onchainCount += count;
                onchainVolume = onchainVolume.add(volume);
                onchainFees = onchainFees.add(fees);
            } else if ("LIGHTNING".equals(network)) {
                lightningCount += count;
                lightningVolume = lightningVolume.add(volume);
                lightningFees = lightningFees.add(fees);
            }
        }

        Map<String, Long> statusCounts = new HashMap<>();
        for (ExternalTransferRepository.StatusAggregate row
                : externalTransferRepository.aggregateOperationalMetricsByStatus()) {
            statusCounts.put(normalize(row.getStatus()), row.getEventCount());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalCount", totalCount);
        payload.put("totalVolumeBtc", totalVolume);
        payload.put("totalFeesBtc", totalFees);
        payload.put("onchainCount", onchainCount);
        payload.put("onchainVolumeBtc", onchainVolume);
        payload.put("onchainFeesBtc", onchainFees);
        payload.put("lightningCount", lightningCount);
        payload.put("lightningVolumeBtc", lightningVolume);
        payload.put("lightningFeesBtc", lightningFees);
        payload.put("inflowBtc", inflow);
        payload.put("outflowBtc", outflow);
        payload.put("confirmedCount", sumStatuses(statusCounts, Set.of("COMPLETED", "SETTLED", "CONFIRMED", "PAID")));
        payload.put("pendingCount", sumStatuses(statusCounts, Set.of("PENDING", "DETECTED", "PROCESSING")));
        payload.put("failedCount", sumStatuses(statusCounts, Set.of("FAILED", "CANCELLED", "EXPIRED")));
        return payload;
    }

    private Map<String, Object> aggregatePaymentLinks() {
        long total = 0;
        long paid = 0;
        long pending = 0;
        long expired = 0;
        long cancelled = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;

        for (PaymentLinkRepository.StatusAggregate row
                : paymentLinkRepository.aggregateOperationalMetricsByStatus()) {
            String status = normalize(row.getStatus());
            long count = row.getLinkCount();
            BigDecimal amount = nz(row.getAmountBtc());
            total += count;
            totalAmount = totalAmount.add(amount);
            if ("PAID".equals(status) || "COMPLETED".equals(status)) {
                paid += count;
                paidAmount = paidAmount.add(amount);
            } else if ("PENDING".equals(status)) {
                pending += count;
            } else if ("EXPIRED".equals(status)) {
                expired += count;
            } else if ("CANCELLED".equals(status)) {
                cancelled += count;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("linksCreated", total);
        payload.put("linksPaid", paid);
        payload.put("linksPending", pending);
        payload.put("linksExpired", expired);
        payload.put("linksCancelled", cancelled);
        payload.put("totalAmountBtc", totalAmount);
        payload.put("paidAmountBtc", paidAmount);
        return payload;
    }

    private long sumStatuses(Map<String, Long> statusCounts, Set<String> statuses) {
        long total = 0;
        for (String status : statuses) {
            total += statusCounts.getOrDefault(status, 0L);
        }
        return total;
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}

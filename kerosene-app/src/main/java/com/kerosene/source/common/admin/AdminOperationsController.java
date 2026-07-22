package source.common.admin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import source.common.financial.FinancialOperationsAdminPort;
import source.common.infra.health.OperationalHealthService;
import source.common.infra.health.OperationalHealthSnapshot;
import source.common.release.ReleaseManifestService;
import source.security.vault.VaultRaftHealthService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/admin/operations")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {

    private final OperationalHealthService operationalHealthService;
    private final ObjectProvider<FinancialOperationsAdminPort> financialOperationsAdminPort;
    private final VaultRaftHealthService vaultRaftHealthService;
    private final ReleaseManifestService releaseManifestService;
    private final MobileDownloadService mobileDownloadService;

    public AdminOperationsController(
            OperationalHealthService operationalHealthService,
            ObjectProvider<FinancialOperationsAdminPort> financialOperationsAdminPort,
            VaultRaftHealthService vaultRaftHealthService,
            ReleaseManifestService releaseManifestService,
            MobileDownloadService mobileDownloadService) {
        this.operationalHealthService = operationalHealthService;
        this.financialOperationsAdminPort = financialOperationsAdminPort;
        this.vaultRaftHealthService = vaultRaftHealthService;
        this.releaseManifestService = releaseManifestService;
        this.mobileDownloadService = mobileDownloadService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("checkedAt", Instant.now());
        payload.put("health", operationalHealthService.dependencies());
        payload.put("blockchain", blockchain());
        payload.put("lightning", lightning());
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
    public Map<String, Object> blockchain() {
        FinancialOperationsAdminPort port = financialOperationsAdminPort.getIfAvailable();
        if (port == null) {
            return financialAdminUnavailable("BITCOIN_CORE_RPC", "KFE financial operations admin port is not configured");
        }
        return port.blockchain();
    }

    @GetMapping("/lightning")
    public Map<String, Object> lightning() {
        FinancialOperationsAdminPort port = financialOperationsAdminPort.getIfAvailable();
        if (port == null) {
            return financialAdminUnavailable("LIGHTNING_PROVIDER", "KFE financial operations admin port is not configured");
        }
        return port.lightning();
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
        FinancialOperationsAdminPort port = financialOperationsAdminPort.getIfAvailable();
        if (port == null) {
            return List.of();
        }
        return port.logs(limit);
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        FinancialOperationsAdminPort port = financialOperationsAdminPort.getIfAvailable();
        if (port == null) {
            return financialAdminUnavailable("KFE_METRICS", "KFE financial operations admin port is not configured");
        }
        return port.metrics();
    }

    private Map<String, Object> financialAdminUnavailable(String primarySource, String message) {
        return Map.of(
                "status", "DOWN",
                "primarySource", primarySource,
                "checkedAt", Instant.now(),
                "message", message);
    }
}

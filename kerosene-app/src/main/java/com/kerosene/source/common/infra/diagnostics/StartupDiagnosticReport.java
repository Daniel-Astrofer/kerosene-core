package source.common.infra.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates startup diagnostics into a single fail-closed status and a log-safe summary.
 */
public class StartupDiagnosticReport {

    private final List<StartupDiagnosticCheck> checks;

    public StartupDiagnosticReport(List<StartupDiagnosticCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    public List<StartupDiagnosticCheck> checks() {
        return checks;
    }

    public StartupDiagnosticStatus status() {
        if (checks.stream().anyMatch(check -> check.status() == StartupDiagnosticStatus.FAIL)) {
            return StartupDiagnosticStatus.FAIL;
        }
        if (checks.stream().anyMatch(check -> check.status() == StartupDiagnosticStatus.WARN)) {
            return StartupDiagnosticStatus.WARN;
        }
        return StartupDiagnosticStatus.OK;
    }

    public long okCount() {
        return count(StartupDiagnosticStatus.OK);
    }

    public long warnCount() {
        return count(StartupDiagnosticStatus.WARN);
    }

    public long failCount() {
        return count(StartupDiagnosticStatus.FAIL);
    }

    /**
     * Renders a compact structured payload for startup logs without resolved secret values.
     */
    public String toLogSummary() {
        List<String> renderedChecks = new ArrayList<>();
        checks.stream()
                .sorted(Comparator.comparing(StartupDiagnosticCheck::name))
                .forEach(check -> renderedChecks.add(renderCheck(check)));
        return "status=" + status()
                + " ok=" + okCount()
                + " warn=" + warnCount()
                + " fail=" + failCount()
                + " checks=[" + String.join("; ", renderedChecks) + "]";
    }

    private long count(StartupDiagnosticStatus status) {
        return checks.stream().filter(check -> check.status() == status).count();
    }

    private String renderCheck(StartupDiagnosticCheck check) {
        String detail = check.details().isEmpty() ? "" : " details=" + String.join("|", check.details());
        return check.name() + ":" + check.status() + ":" + check.message() + detail;
    }
}

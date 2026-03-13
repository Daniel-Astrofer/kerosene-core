package source.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ─── Phase 4: RAM-Only Telemetry (Low-Trace Monitoring) ─────────────────────
 *
 * Design Principles:
 * - ALL metrics live exclusively in JVM heap — zero disk writes.
 * - Uses SimpleMeterRegistry (in-process only), NOT Prometheus/Graphite
 * exports.
 * - Recent event log capped at MAX_EVENTS entries to bound memory usage.
 * - Exposed only at GET /sovereignty/telemetry (internal network).
 *
 * Tracked signals:
 * - quorum.failures : number of consecutive quorum NACK rounds
 * - stall.events : number of times STALL mode was entered
 * - heartbeat.failures : Vault heartbeat timeouts/errors
 * - tpm.checks : total PCR polling rounds executed
 * - tpm.mismatches : number of PCR quote deviations detected
 * - suicide.triggers : number of triggerInstantSuicide calls
 * - transactions.proposed : total transactions sent to quorum
 * - transactions.accepted : transactions that reached 2PC COMMIT
 */
@Service
public class TelemetryService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryService.class);
    private static final int MAX_EVENTS = 200; // RAM cap for the event ring buffer

    private final MeterRegistry registry;

    // Counters — all RAM-resident, never flushed to disk
    private final Counter quorumFailures;
    private final Counter stallEvents;
    private final Counter heartbeatFailures;
    private final Counter tpmChecks;
    private final Counter tpmMismatches;
    private final Counter suicideTriggers;
    private final Counter transactionsProposed;
    private final Counter transactionsAccepted;

    // Ring buffer of last N security events (RAM only, bounded)
    private final ConcurrentLinkedDeque<String> recentEvents = new ConcurrentLinkedDeque<>();
    private final AtomicLong eventSeq = new AtomicLong(0);

    public TelemetryService(MeterRegistry registry) {
        this.registry = registry;

        quorumFailures = Counter.builder("kerosene.quorum.failures")
                .description("Quorum NACK rounds").register(registry);
        stallEvents = Counter.builder("kerosene.stall.events")
                .description("STALL mode activations").register(registry);
        heartbeatFailures = Counter.builder("kerosene.heartbeat.failures")
                .description("Vault heartbeat errors").register(registry);
        tpmChecks = Counter.builder("kerosene.tpm.checks")
                .description("PCR polling rounds completed").register(registry);
        tpmMismatches = Counter.builder("kerosene.tpm.mismatches")
                .description("PCR quote deviations detected").register(registry);
        suicideTriggers = Counter.builder("kerosene.suicide.triggers")
                .description("triggerInstantSuicide invocations").register(registry);
        transactionsProposed = Counter.builder("kerosene.transactions.proposed")
                .description("Transactions proposed to quorum").register(registry);
        transactionsAccepted = Counter.builder("kerosene.transactions.accepted")
                .description("Transactions that reached 2PC COMMIT").register(registry);

        log.info("[Telemetry] RAM-only metrics initialized. No disk writes will occur.");
    }

    // ── Increment APIs ────────────────────────────────────────────────────────

    public void recordQuorumFailure(String reason) {
        quorumFailures.increment();
        addEvent("QUORUM_FAILURE: " + reason);
    }

    public void recordStallEvent(String reason) {
        stallEvents.increment();
        addEvent("STALL_MODE_ENTERED: " + reason);
        log.warn("[Telemetry] STALL event recorded. Total stalls: {}", (long) stallEvents.count());
    }

    public void recordHeartbeatFailure(String endpoint) {
        heartbeatFailures.increment();
        addEvent("HEARTBEAT_FAILURE: " + endpoint);
    }

    public void recordTpmCheck(boolean passed) {
        tpmChecks.increment();
        if (!passed) {
            tpmMismatches.increment();
            addEvent("TPM_MISMATCH_DETECTED");
            log.error("[Telemetry] TPM mismatch #{} detected.", (long) tpmMismatches.count());
        }
    }

    public void recordSuicideTrigger(String reason) {
        suicideTriggers.increment();
        addEvent("SUICIDE_TRIGGERED: " + reason);
    }

    public void recordTransactionProposed() {
        transactionsProposed.increment();
    }

    public void recordTransactionAccepted() {
        transactionsAccepted.increment();
    }

    // ── Snapshot API (for /sovereignty/telemetry endpoint) ───────────────────

    /**
     * Returns a point-in-time snapshot of all metrics.
     * This is read-only — never triggers any writes or exports.
     */
    public Map<String, Object> snapshot() {
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("snapshotAt", Instant.now().toString());
        snap.put("storage", "RAM_ONLY — no disk persistence");

        Map<String, Object> counters = new LinkedHashMap<>();
        counters.put("quorumFailures", (long) quorumFailures.count());
        counters.put("stallEvents", (long) stallEvents.count());
        counters.put("heartbeatFailures", (long) heartbeatFailures.count());
        counters.put("tpmChecksTotal", (long) tpmChecks.count());
        counters.put("tpmMismatches", (long) tpmMismatches.count());
        counters.put("suicideTriggers", (long) suicideTriggers.count());
        counters.put("transactionsProposed", (long) transactionsProposed.count());
        counters.put("transactionsAccepted", (long) transactionsAccepted.count());
        snap.put("counters", counters);

        snap.put("recentEvents", recentEvents.stream().toList());
        return snap;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void addEvent(String event) {
        long seq = eventSeq.incrementAndGet();
        recentEvents.addLast(String.format("[%s] #%d %s", Instant.now(), seq, event));
        // Keep the ring buffer bounded at MAX_EVENTS entries
        while (recentEvents.size() > MAX_EVENTS) {
            recentEvents.pollFirst();
        }
    }

    /**
     * Provide a SimpleMeterRegistry bean so Spring uses in-process metrics only.
     * This intentionally overrides any auto-configured Prometheus/Graphite exporter
     * in the event those dependencies are added to the classpath in the future.
     */
    @Bean
    public static MeterRegistry keroseneInMemoryRegistry() {
        return new SimpleMeterRegistry();
    }
}

package source.common.infra.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Named log domains for the Kerosene financial platform.
 *
 * <p>Use these markers to route log events to their dedicated appender/stream.
 * Each domain corresponds to a separate appender in {@code logback-spring.xml}
 * and can be tailed or filtered independently in production.
 *
 * <p>Usage example:
 * <pre>{@code
 * private static final Logger log = LoggerFactory.getLogger(MyService.class);
 *
 * // Routes to the TRANSACTIONS appender
 * log.info(LogDomain.TRANSACTIONS, "payment.initiated amount={} sats={}", btc, sats);
 *
 * // Routes to the AUDIT appender
 * log.warn(LogDomain.AUDIT, "reconciliation.mismatch ledgerBalance={} nodeBalance={}", l, n);
 * }</pre>
 *
 * <p><b>Domain definitions:</b>
 * <ul>
 *   <li>{@link #SECURITY} — auth, passkeys, honeypot, token issuance, MFA</li>
 *   <li>{@link #TRANSACTIONS} — payments, Lightning, on-chain, wallet operations</li>
 *   <li>{@link #TREASURY} — reserve, liquidity, rebalancing, cold wallet</li>
 *   <li>{@link #AUDIT} — ledger integrity, reconciliation, quorum/raft events</li>
 *   <li>{@link #ACCESS} — HTTP access log (method, path, status, duration)</li>
 * </ul>
 *
 * Events without a marker route to the general {@code APPLICATION} appender.
 */
public final class LogDomain {

    /** Auth flows, JWT issuance, passkeys, honeypot triggers, MFA enforcement. */
    public static final Marker SECURITY     = MarkerFactory.getMarker("SECURITY");

    /** Payments, Lightning invoices, on-chain sends/receives, wallet balance changes. */
    public static final Marker TRANSACTIONS = MarkerFactory.getMarker("TRANSACTIONS");

    /** Reserve snapshots, liquidity rebalancing, cold-wallet sweeps, treasury overview. */
    public static final Marker TREASURY     = MarkerFactory.getMarker("TREASURY");

    /**
     * Immutable business events: ledger mutations, reconciliation results,
     * quorum decisions, shard operations. These logs should never be dropped.
     */
    public static final Marker AUDIT        = MarkerFactory.getMarker("AUDIT");

    /** HTTP request/response access log (no body, no PII, only metadata). */
    public static final Marker ACCESS       = MarkerFactory.getMarker("ACCESS");

    private LogDomain() {
    }
}

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
 * <p><b>Domain definitions:</b> runtime, startup, security, auth, kfe, audit,
 * integration, vault, mpc, frontend-api and access.
 *
 * Events without a marker route to the general {@code APPLICATION} appender.
 */
public final class LogDomain {

    /** General application runtime diagnostics. */
    public static final Marker RUNTIME = MarkerFactory.getMarker("RUNTIME");

    /** Startup, readiness, profile and configuration diagnostics. */
    public static final Marker STARTUP = MarkerFactory.getMarker("STARTUP");

    /** Security controls, fail-closed decisions, policy denials and suspicious activity. */
    public static final Marker SECURITY = MarkerFactory.getMarker("SECURITY");

    /** Auth flows, JWT/session issuance, passkeys, device keys and MFA enforcement. */
    public static final Marker AUTH = MarkerFactory.getMarker("AUTH");

    /** KFE financial engine events: wallets, ledger, transactions, outbox and settlement. */
    public static final Marker KFE = MarkerFactory.getMarker("KFE");

    /** External integration calls that are not Vault/MPC-specific. */
    public static final Marker INTEGRATION = MarkerFactory.getMarker("INTEGRATION");

    /** Vault calls, readiness, attestation and key-management diagnostics. */
    public static final Marker VAULT = MarkerFactory.getMarker("VAULT");

    /** MPC calls, signer diagnostics and threshold-policy decisions. */
    public static final Marker MPC = MarkerFactory.getMarker("MPC");

    /** Frontend/backend API communication diagnostics excluding raw bodies. */
    public static final Marker FRONTEND_API = MarkerFactory.getMarker("FRONTEND_API");

    /**
     * Immutable business events: ledger mutations, reconciliation results,
     * quorum decisions, shard operations. These logs should never be dropped.
     */
    public static final Marker AUDIT = MarkerFactory.getMarker("AUDIT");

    /** HTTP request/response access log (no body, no PII, only metadata). */
    public static final Marker ACCESS = MarkerFactory.getMarker("ACCESS");

    /** @deprecated Use {@link #KFE}. */
    @Deprecated(forRemoval = false)
    public static final Marker TRANSACTIONS = KFE;

    /** @deprecated Use {@link #KFE} or {@link #AUDIT}, depending on event semantics. */
    @Deprecated(forRemoval = false)
    public static final Marker TREASURY = KFE;

    private LogDomain() {
    }
}

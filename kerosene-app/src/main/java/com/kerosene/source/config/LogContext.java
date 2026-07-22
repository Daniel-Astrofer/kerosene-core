package source.config;

import org.slf4j.MDC;

import java.util.function.Supplier;

/**
 * Utility for adding operation-level context to the current log line via MDC.
 *
 * Usage examples:
 * 
 * <pre>
 * // Simple tag
 * LogContext.operation("LOGIN");
 * log.info("User authenticated");
 * LogContext.clearOperation();
 *
 * // Timed block — auto-clears MDC after completion
 * LedgerEntity result = LogContext.timed("FETCH_LEDGER", () -> repo.findByWalletId(id));
 * </pre>
 *
 * Keys set here are all safe — they describe WHAT happened, not WHO or WHAT
 * value.
 */
public final class LogContext {

    public static final String KEY_OPERATION = "operation";
    public static final String KEY_DURATION_MS = "durationMs";

    private LogContext() {
    }

    /** Tag the current thread's MDC with an operation label. */
    public static void operation(String name) {
        MDC.put(KEY_OPERATION, name);
    }

    /** Remove the operation tag from the current thread's MDC. */
    public static void clearOperation() {
        MDC.remove(KEY_OPERATION);
        MDC.remove(KEY_DURATION_MS);
    }

    /**
     * Execute {@code supplier} and record elapsed millis as {@code durationMs} in
     * MDC.
     * <p>
     * The operation tag and duration are cleared after the call (success or
     * failure).
     * </p>
     *
     * @param operationName label for this operation — safe description only, no PII
     * @param supplier      the work to perform and measure
     * @return the value returned by the supplier
     */
    public static <T> T timed(String operationName, Supplier<T> supplier) {
        MDC.put(KEY_OPERATION, operationName);
        long start = System.currentTimeMillis();
        try {
            return supplier.get();
        } finally {
            MDC.put(KEY_DURATION_MS, String.valueOf(System.currentTimeMillis() - start));
        }
    }

    /**
     * Execute {@code runnable} and record elapsed millis as {@code durationMs} in
     * MDC.
     */
    public static void timed(String operationName, Runnable runnable) {
        MDC.put(KEY_OPERATION, operationName);
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            MDC.put(KEY_DURATION_MS, String.valueOf(System.currentTimeMillis() - start));
        }
    }
}

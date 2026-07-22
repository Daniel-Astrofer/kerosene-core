package source.common.infra.logging;

/**
 * Stable structured log field names used across runtime, access and audit logs.
 *
 * <p>Values must be safe for operators and log sinks: never place request bodies,
 * credentials, tokens, invoices, macaroons, private keys or raw provider payloads
 * in these fields.
 */
public final class StructuredLogField {

    public static final String TRACE_ID = "traceId";
    public static final String CORRELATION_ID = "correlationId";
    public static final String EVENT = "event";
    public static final String DOMAIN = "domain";
    public static final String OPERATION = "operation";
    public static final String MESSAGE = "safeMessage";
    public static final String ERROR_CODE = "errorCode";
    public static final String EXCEPTION_TYPE = "exceptionType";
    public static final String HTTP_METHOD = "http.method";
    public static final String URL_PATH = "url.path";
    public static final String HTTP_STATUS_CODE = "http.status_code";
    public static final String DURATION_MS = "duration_ms";
    public static final String CLIENT_IP = "client.ip";
    public static final String REQUEST_BYTES = "req.bytes";
    public static final String RESPONSE_BYTES = "res.bytes";

    private StructuredLogField() {
    }
}

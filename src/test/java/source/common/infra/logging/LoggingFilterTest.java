package source.common.infra.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoggingFilterTest {

    private final LoggingFilter filter = new LoggingFilter();

    @Test
    void shouldOmitBodiesForSensitiveAuthRoutes() {
        String result = invokeSafeBodyForLogging("/auth/login", "{\"data\":\"secret\"}");
        assertEquals("[OMITTED_FOR_SECURITY]", result);
    }

    @Test
    void shouldMaskSessionArtifactsForNonSensitiveRoutes() {
        String result = invokeSafeBodyForLogging(
                "/transactions/status",
                "{\"sessionId\":\"abc\",\"preAuthToken\":\"def\",\"token\":\"ghi\"}");
        assertEquals("{\"sessionId\":\"***MASKED***\",\"preAuthToken\":\"***MASKED***\",\"token\":\"***MASKED***\"}",
                result);
    }

    private String invokeSafeBodyForLogging(String uri, String payload) {
        try {
            java.lang.reflect.Method method = LoggingFilter.class
                    .getDeclaredMethod("safeBodyForLogging", String.class, String.class);
            method.setAccessible(true);
            return (String) method.invoke(filter, uri, payload);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

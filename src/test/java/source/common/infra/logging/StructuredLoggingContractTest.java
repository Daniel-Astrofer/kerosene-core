package source.common.infra.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class StructuredLoggingContractTest {

    private final LogContextFilter filter = new LogContextFilter();

    @Test
    void acceptsSafeCorrelationIdAndReturnsItOnResponse() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader("X-Correlation-Id", "web-req_123.abc:/@trace");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(StructuredLogField.CORRELATION_ID)).isEqualTo("web-req_123.abc:/@trace"));

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("web-req_123.abc:/@trace");
        assertThat(MDC.get(StructuredLogField.CORRELATION_ID)).isNull();
    }

    @Test
    void fallsBackToSafeRequestIdWhenCorrelationIdIsUnsafe() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader("X-Correlation-Id", "unsafe value with spaces");
        request.addHeader("X-Request-Id", "request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(StructuredLogField.CORRELATION_ID)).isEqualTo("request-123"));

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("request-123");
    }

    @Test
    void generatesCorrelationIdWhenRequestHeadersAreAbsentOrUnsafe() throws ServletException, IOException {
        MockHttpServletRequest request = request();
        request.addHeader("X-Request-Id", "bad\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) ->
                assertThat(MDC.get(StructuredLogField.CORRELATION_ID)).isNotBlank());

        assertThat(response.getHeader("X-Correlation-Id")).isNotBlank();
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/kfe/health");
        request.setServerName("localhost");
        return request;
    }
}

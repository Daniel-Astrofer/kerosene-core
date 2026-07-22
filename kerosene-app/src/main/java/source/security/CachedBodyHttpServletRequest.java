package source.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wraps the HttpServletRequest to allow its body to be read multiple times.
 *
 * <p>In Spring, {@link HttpServletRequest#getInputStream()} can only be consumed once.
 * This wrapper caches the body on first access so that both the
 * {@link HoneypotRequestFilter} and the downstream
 * {@link org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter}
 * can both read it without conflict.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        InputStream requestInputStream = request.getInputStream();
        this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
    }

    /**
     * @return The raw cached request body bytes; never {@code null}.
     */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    /**
     * Lightweight {@link ServletInputStream} backed by a byte array.
     */
    private static class CachedBodyServletInputStream extends ServletInputStream {

        private final InputStream delegate;

        public CachedBodyServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate.available() == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Async not supported for cached body stream");
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }
    }
}

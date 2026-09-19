package com.devpilot.common.web;

import com.devpilot.common.config.DevPilotProperties;
import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 body 크기 상한 (docs/03 §3.1, docs/05 §1.4.3 2단계). {@code Content-Length}가 상한을 넘으면 바로 413 {@code
 * REQUEST_TOO_LARGE}를 쓰고, 길이를 모르는 요청(chunked)은 읽는 동안 세다가 넘으면 {@link RequestBodyTooLargeException}을
 * 던진다(GlobalExceptionHandler가 같은 413으로 바꾼다).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ProblemResponseWriter problemResponseWriter;

    public RequestBodySizeLimitFilter(
            DevPilotProperties properties, ProblemResponseWriter problemResponseWriter) {
        this.maxBytes = properties.security().maxRequestBodyBytes();
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBytes) {
            problemResponseWriter.write(request, response, ErrorCode.REQUEST_TOO_LARGE);
            return;
        }
        filterChain.doFilter(new LimitedRequest(request, maxBytes), response);
    }

    /** 실제로 읽은 바이트 수로 상한을 확인하는 요청 래퍼. */
    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long maxBytes;
        private LimitedInputStream stream;

        LimitedRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), maxBytes);
            }
            return stream;
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private long readBytes;

        LimitedInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int read) throws RequestBodyTooLargeException {
            readBytes += read;
            if (readBytes > maxBytes) {
                throw new RequestBodyTooLargeException(maxBytes);
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}

package com.devpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.testsupport.UnitTest;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@UnitTest
class TraceIdFilterTest {

    private static final String VALID_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void shouldEchoTraceIdWhenHeaderIsValid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(TraceIdFilter.HEADER, VALID_TRACE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        filter.doFilter(
                request, response, (req, res) -> seenInChain.set(MDC.get(TraceIdFilter.MDC_KEY)));

        assertThat(response.getHeader(TraceIdFilter.HEADER)).isEqualTo(VALID_TRACE_ID);
        assertThat(seenInChain.get()).isEqualTo(VALID_TRACE_ID);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void shouldGenerateTraceIdWhenHeaderIsInvalid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(TraceIdFilter.HEADER, "NOT-A-TRACE-ID");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(TraceIdFilter.HEADER))
                .matches("^[0-9a-f]{32}$")
                .isNotEqualTo("NOT-A-TRACE-ID");
    }
}

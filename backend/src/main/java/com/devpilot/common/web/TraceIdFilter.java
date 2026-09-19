package com.devpilot.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 traceId를 정한다 (docs/03 §3.1, docs/05 §1.4.3 1단계). 요청 {@code X-Trace-Id}가 32자리 소문자 hex면 그대로
 * 쓰고, 아니면 새로 만든다. MDC {@code traceId}, 요청 속성, 응답 헤더에 같은 값을 둔다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    public static final String REQUEST_ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";

    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = resolve(request.getHeader(HEADER));
        request.setAttribute(REQUEST_ATTRIBUTE, traceId);
        response.setHeader(HEADER, traceId);
        MDC.put(MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String headerValue) {
        if (headerValue != null && VALID_TRACE_ID.matcher(headerValue).matches()) {
            return headerValue;
        }
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    /** 이 요청의 traceId. 필터를 거치지 않은 경우(단위 테스트 등)에는 새로 만든다. */
    public static String currentTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof String traceId) {
            return traceId;
        }
        String fromMdc = MDC.get(MDC_KEY);
        return fromMdc != null ? fromMdc : resolve(null);
    }
}

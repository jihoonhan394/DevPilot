package com.devpilot.common.web;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 한도 (docs/07 §12.3, docs/05 §1.4.3 4단계, BL-SEC-11). Spring Security chain에서 {@code
 * BearerTokenAuthenticationFilter} 다음, {@code UserContextFilter} 앞에 둔다 — allowlist 거부 대상 사용자도 제한된다.
 *
 * <ul>
 *   <li>인증된 {@code /api/v1/**}: JWT {@code sub}당 {@code requests-per-minute}(120)
 *   <li>{@code POST /api/v1/dev/token}(인증 없음): IP당 {@code dev-token-per-hour-per-ip}(30)
 * </ul>
 *
 * 초과는 429 {@code RATE_LIMITED} + {@code Retry-After}(다음 토큰까지 초, 올림, 최소 1). 캘린더 피드 한도는 피드가 생기는 S5에
 * 붙는다. 이 필터는 bean이 아니라 {@code SecurityConfig}가 만든다(서블릿 필터로 두 번 등록되지 않게).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1/";
    private static final String DEV_TOKEN_PATH = "/api/v1/dev/token";

    private final ProblemResponseWriter problemResponseWriter;
    private final TokenBucketRateLimiter userLimiter;
    private final TokenBucketRateLimiter devTokenLimiter;

    public RateLimitFilter(
            ProblemResponseWriter problemResponseWriter,
            TokenBucketRateLimiter userLimiter,
            TokenBucketRateLimiter devTokenLimiter) {
        this.problemResponseWriter = Objects.requireNonNull(problemResponseWriter);
        this.userLimiter = Objects.requireNonNull(userLimiter);
        this.devTokenLimiter = Objects.requireNonNull(devTokenLimiter);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        TokenBucketRateLimiter.@Nullable Decision decision = null;
        if ("POST".equals(request.getMethod()) && DEV_TOKEN_PATH.equals(path)) {
            decision = devTokenLimiter.tryAcquire(request.getRemoteAddr());
        } else if (path.startsWith(API_PREFIX)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt) {
                String subject = jwt.getToken().getSubject();
                if (subject != null) {
                    decision = userLimiter.tryAcquire(subject);
                }
            }
        }
        if (decision != null && !decision.allowed()) {
            response.setHeader(
                    HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            problemResponseWriter.write(request, response, ErrorCode.RATE_LIMITED);
            return;
        }
        chain.doFilter(request, response);
    }
}

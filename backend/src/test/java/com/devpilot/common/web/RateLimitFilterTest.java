package com.devpilot.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.devpilot.common.error.ErrorCode;
import com.devpilot.common.error.ProblemDetailFactory;
import com.devpilot.common.error.ProblemResponseWriter;
import com.devpilot.testsupport.MutableClock;
import com.devpilot.testsupport.UnitTest;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** BL-SEC-11 {@link RateLimitFilter}: JWT {@code sub}당 한도, dev token IP당 한도, 429 응답 형식. */
@UnitTest
class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-05T10:00:00Z"));
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final RateLimitFilter filter =
            new RateLimitFilter(
                    problemResponseWriter(),
                    new TokenBucketRateLimiter(
                            2, Duration.ofMinutes(1), clock, TokenBucketRateLimiter.Limits.DEFAULT),
                    new TokenBucketRateLimiter(
                            1, Duration.ofHours(1), clock, TokenBucketRateLimiter.Limits.DEFAULT));
    private final AtomicInteger passed = new AtomicInteger();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldLimitAuthenticatedRequestsPerSubject() throws Exception {
        authenticate("subject-a");

        assertThat(perform("GET", "/api/v1/today").getStatus()).isEqualTo(200);
        assertThat(perform("POST", "/api/v1/today/generate").getStatus()).isEqualTo(200);
        MockHttpServletResponse rejected = perform("GET", "/api/v1/reviews/due");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("30");
        JsonNode body = jsonMapper.readTree(rejected.getContentAsString());
        assertThat(body.path("code").asString()).isEqualTo("RATE_LIMITED");
        assertThat(body.path("status").asInt()).isEqualTo(429);
        assertThat(passed).hasValue(2);

        authenticate("subject-b");
        assertThat(perform("GET", "/api/v1/today").getStatus()).isEqualTo(200);
    }

    @Test
    void shouldAllowAgainAfterRefill() throws Exception {
        authenticate("subject-a");
        perform("GET", "/api/v1/today");
        perform("GET", "/api/v1/today");

        clock.advance(Duration.ofSeconds(30));

        assertThat(perform("GET", "/api/v1/today").getStatus()).isEqualTo(200);
    }

    @Test
    void shouldLimitDevTokenPerRemoteAddress() throws Exception {
        assertThat(perform("POST", "/api/v1/dev/token").getStatus()).isEqualTo(200);
        MockHttpServletResponse rejected = perform("POST", "/api/v1/dev/token");

        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("3600");
    }

    @Test
    void shouldPassUnauthenticatedAndNonApiRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(perform("GET", "/api/v1/me").getStatus()).isEqualTo(200);
            assertThat(perform("GET", "/actuator/health").getStatus()).isEqualTo(200);
        }
        authenticate("subject-a");
        for (int i = 0; i < 5; i++) {
            assertThat(perform("GET", "/actuator/health").getStatus()).isEqualTo(200);
        }
    }

    private MockHttpServletResponse perform(String method, String path)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
        return response;
    }

    private static void authenticate(String subject) {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "ES256")
                        .subject(subject)
                        .issuedAt(Instant.parse("2026-10-05T09:59:00Z"))
                        .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private ProblemResponseWriter problemResponseWriter() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage(ErrorCode.RATE_LIMITED.messageKey(), Locale.KOREAN, "요청이 너무 많습니다.");
        return new ProblemResponseWriter(new ProblemDetailFactory(messages), jsonMapper);
    }
}

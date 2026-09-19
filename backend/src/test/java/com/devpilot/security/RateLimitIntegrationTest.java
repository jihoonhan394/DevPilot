package com.devpilot.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * docs/07 §12.3 (BL-SEC-11), AC-08: 필터 체인 안에서 JWT {@code sub}당 한도를 넘으면 429 {@code RATE_LIMITED} +
 * {@code Retry-After}. 한도를 분당 3회로 낮춘 별도 context다(운영 기본 120회는 {@code RuleSettingsFactoryTest}가
 * 확인한다).
 */
@IntegrationTest
@TestPropertySource(properties = "devpilot.security.rate-limit.requests-per-minute=3")
class RateLimitIntegrationTest extends ApiTestSupport {

    @Test
    void shouldRejectRequestsOverLimitPerSubject() throws Exception {
        TestUser user = TestUser.owner();
        for (int i = 0; i < 3; i++) {
            api.get(user, "/api/v1/me").andExpect(status().isOk());
        }

        api.get(user, "/api/v1/me")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "20"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.status").value(429));
        api.get(TestUser.owner(), "/api/v1/me").andExpect(status().isOk());

        clock.advance(Duration.ofSeconds(20));
        api.get(user, "/api/v1/me").andExpect(status().isOk());
    }

    @Test
    void shouldAuthenticateBeforeLimiting() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        }
    }

    @Test
    void shouldLimitUsersOutsideAllowlistToo() throws Exception {
        TestUser stranger = TestUser.stranger();
        for (int i = 0; i < 3; i++) {
            api.get(stranger, "/api/v1/me").andExpect(status().isForbidden());
        }

        api.get(stranger, "/api/v1/me").andExpect(status().isTooManyRequests());
    }
}

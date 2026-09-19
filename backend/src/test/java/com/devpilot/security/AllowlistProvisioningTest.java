package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** docs/07 §16 ST-02, docs/03 §4.3 (BL-SEC-04), AC-18: JIT 프로비저닝과 요청마다 allowlist 재확인. */
@IntegrationTest
class AllowlistProvisioningTest extends ApiTestSupport {

    @Test
    void shouldProvisionUserOnceWhenAllowlistedUserCallsTwice() throws Exception {
        TestUser user = TestUser.owner();

        try (AuditLogCapture audit = AuditLogCapture.start()) {
            String first = api.body(api.get(user, "/api/v1/me")).path("id").asString();
            String second = api.body(api.get(user, "/api/v1/me")).path("id").asString();

            assertThat(first).isEqualTo(second);
            assertThat(audit.events()).containsExactly("AUTH_USER_PROVISIONED");
        }
        assertThat(
                        count(
                                "select count(*) from devpilot.app_user where external_auth_id = ?",
                                user.sub()))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectAndNotCreateUserWhenEmailIsNotAllowlisted() throws Exception {
        TestUser stranger = TestUser.stranger();

        try (AuditLogCapture audit = AuditLogCapture.start()) {
            api.get(stranger, "/api/v1/me")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("USER_NOT_ALLOWED"));

            assertThat(audit.events()).containsExactly("AUTH_USER_REJECTED");
        }
        assertThat(userId(stranger)).isNull();
    }

    @Test
    void shouldRejectExistingUserWhenTokenEmailLeavesAllowlist() throws Exception {
        TestUser user = TestUser.owner();
        api.get(user, "/api/v1/me").andExpect(status().isOk());
        String token = api.token(user, builder -> builder.claim("email", "stranger@devpilot.test"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_NOT_ALLOWED"));
    }

    @Test
    void shouldRejectAnonymousToken() throws Exception {
        TestUser user = TestUser.owner();
        String token = api.token(user, builder -> builder.claim("is_anonymous", true));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_NOT_ALLOWED"));
        assertThat(userId(user)).isNull();
    }

    @Test
    void shouldCreateSingleRowWhenFirstRequestsRace() throws Exception {
        TestUser user = TestUser.invited();
        String token = api.token(user);
        int threads = 4;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call =
                        () -> {
                            start.await();
                            return mockMvc.perform(
                                            get("/api/v1/me")
                                                    .header(
                                                            HttpHeaders.AUTHORIZATION,
                                                            "Bearer " + token))
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                results.add(executor.submit(call));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(
                        count(
                                "select count(*) from devpilot.app_user where external_auth_id = ?",
                                user.sub()))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectNonUuidSubjectAsUnauthenticated() throws Exception {
        TestUser user = TestUser.owner();
        String token = api.token(user, builder -> builder.subject("not-a-uuid"));

        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}

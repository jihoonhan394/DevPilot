package com.devpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** docs/07 §16 ST-05, docs/05 §3.4 (BL-SEC-14), AC-15: 삭제 요청 후에는 GET·DELETE /me만 허용한다. */
@IntegrationTest
class DeletionRequestedAccessTest extends ApiTestSupport {

    @Test
    void shouldAcceptDeletionAndAuditWhenLoginIsRecent() throws Exception {
        TestUser user = onboardedOwner();

        try (AuditLogCapture audit = AuditLogCapture.start()) {
            api.delete(user, "/api/v1/me")
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.status").value("DELETION_REQUESTED"))
                    .andExpect(jsonPath("$.deletionRequestedAt").value("2026-10-05T10:00:00Z"));

            assertThat(audit.events()).containsExactly("ACCOUNT_DELETION_REQUESTED");
        }
        assertThat(
                        count(
                                "select count(*) from devpilot.app_user where id = ? and status ="
                                        + " 'DELETION_REQUESTED' and calendar_token_hash is null",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldAllowOnlyGetAndDeleteMeAfterDeletionRequest() throws Exception {
        TestUser user = onboardedOwner();
        api.delete(user, "/api/v1/me").andExpect(status().isAccepted());

        api.get(user, "/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELETION_REQUESTED"))
                .andExpect(jsonPath("$.deletionRequestedAt").value("2026-10-05T10:00:00Z"));
        api.patch(user, "/api/v1/me", Map.of("dayStartHour", 5, "version", 1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.get(user, "/api/v1/plans/active")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.get(user, "/api/v1/skills/tree")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        api.post(user, "/api/v1/side-projects", TestApi.sideProjectRequest("차단"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void shouldAcceptRepeatedDeleteWithoutRecentLoginCheck() throws Exception {
        TestUser user = TestUser.owner();
        String oldToken = api.token(user);
        api.delete(user, "/api/v1/me").andExpect(status().isAccepted());
        clock.advance(Duration.ofMinutes(30));

        mockMvc.perform(
                        delete("/api/v1/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deletionRequestedAt").value("2026-10-05T10:00:00Z"));
    }

    @Test
    void shouldAllowDeletionBeforeOnboarding() throws Exception {
        api.delete(TestUser.owner(), "/api/v1/me").andExpect(status().isAccepted());
    }
}

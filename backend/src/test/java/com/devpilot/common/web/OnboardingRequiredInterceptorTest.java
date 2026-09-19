package com.devpilot.common.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** AC-11 S4, docs/05 §1.4.4: 온보딩 전에는 허용 목록 밖 endpoint가 409 {@code ONBOARDING_REQUIRED}다. */
@IntegrationTest
class OnboardingRequiredInterceptorTest extends ApiTestSupport {

    @ParameterizedTest(name = "[{index}] GET {0}")
    @ValueSource(
            strings = {
                "/api/v1/plans/active",
                "/api/v1/plans",
                "/api/v1/side-projects",
                "/api/v1/learning-goal",
                "/api/v1/skills/me"
            })
    void shouldRequireOnboardingForUserScopedReads(String path) throws Exception {
        api.get(TestUser.owner(), path)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void shouldRequireOnboardingForSideProjectCreation() throws Exception {
        api.post(TestUser.owner(), "/api/v1/side-projects", TestApi.sideProjectRequest("온보딩 전"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    @Test
    void shouldAllowProfileAndSkillTreeBeforeOnboarding() throws Exception {
        TestUser user = TestUser.owner();

        api.get(user, "/api/v1/me").andExpect(status().isOk());
        api.patch(user, "/api/v1/me", Map.of("weekdayStudyMinutes", 60, "version", 0))
                .andExpect(status().isOk());
        api.get(user, "/api/v1/skills/tree").andExpect(status().isOk());
    }

    @Test
    void shouldOpenEndpointsAfterOnboarding() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, "/api/v1/plans/active").andExpect(status().isOk());
        api.get(user, "/api/v1/side-projects").andExpect(status().isOk());
        api.get(user, "/api/v1/skills/me").andExpect(status().isOk());
    }
}

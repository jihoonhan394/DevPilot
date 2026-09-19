package com.devpilot.goal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** docs/05 §5.1·§5.2 (BL-GOL-01), AC-01 S1·S5. */
@IntegrationTest
class LearningGoalServiceIntegrationTest extends ApiTestSupport {

    private static final String GOAL = "/api/v1/learning-goal";

    @Test
    void shouldReturnOnboardingValuesWhenReadAgain() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, GOAL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetRole").value("JAVA_BACKEND"))
                .andExpect(jsonPath("$.checkpointDate").doesNotExist())
                .andExpect(jsonPath("$.targetCompletionDate").value("2027-04-01"))
                .andExpect(jsonPath("$.focusSkills.length()").value(1))
                .andExpect(jsonPath("$.focusSkills[0].code").value("SPRING.TRANSACTION"))
                .andExpect(jsonPath("$.focusSkills[0].category").value("SPRING"))
                .andExpect(jsonPath("$.replanRecommended").value(false))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldKeepMilestonesStableAcrossReadsWithNewToken() throws Exception {
        TestUser user = onboardedOwner();

        JsonNode first = activePlan(user).path("milestones");
        JsonNode second = activePlan(user).path("milestones");

        assertThat(second).isEqualTo(first);
        assertThat(first).hasSize(3);
    }

    @Test
    void shouldRecommendReplanWithoutNewVersionWhenTargetDateChanges() throws Exception {
        TestUser user = onboardedOwner();

        api.put(user, GOAL, request("2027-06-30", List.of("SPRING.TRANSACTION"), 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCompletionDate").value("2027-06-30"))
                .andExpect(jsonPath("$.replanRecommended").value(true))
                .andExpect(jsonPath("$.version").value(1));

        JsonNode plan = activePlan(user);
        assertThat(plan.path("replanRecommended").asBoolean()).isTrue();
        assertThat(plan.path("planVersion").asInt()).isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldNotRecommendReplanWhenOnlyFocusSkillsChange() throws Exception {
        TestUser user = onboardedOwner();

        api.put(user, GOAL, request("2027-04-01", List.of("DATABASE.INDEX", "JAVA.EXCEPTION"), 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusSkills.length()").value(2))
                .andExpect(jsonPath("$.replanRecommended").value(false));

        assertThat(activePlan(user).path("replanRecommended").asBoolean()).isFalse();
    }

    @Test
    void shouldRejectStaleVersionWithoutChangingGoal() throws Exception {
        TestUser user = onboardedOwner();
        api.put(user, GOAL, request("2027-05-01", List.of(), 0)).andExpect(status().isOk());

        api.put(user, GOAL, request("2027-07-01", List.of(), 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        api.get(user, GOAL).andExpect(jsonPath("$.targetCompletionDate").value("2027-05-01"));
    }

    @Test
    void shouldRejectTargetDateOutsideTomorrowToThreeYears() throws Exception {
        TestUser user = onboardedOwner();

        api.put(user, GOAL, request("2026-10-05", List.of(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("targetCompletionDate"))
                .andExpect(jsonPath("$.errors[0].code").value("DATE_OUT_OF_RANGE"));
        api.put(user, GOAL, request("2029-10-06", List.of(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("DATE_OUT_OF_RANGE"));
        api.put(user, GOAL, request("2029-10-05", List.of(), 0)).andExpect(status().isOk());
    }

    @Test
    void shouldRejectPropertiesOutsideGoalModel() throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> body = request("2027-04-01", List.of(), 0);
        body.put("checkpointDate", "2027-01-05");

        api.put(user, GOAL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        api.get(user, GOAL).andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void shouldRejectUnknownFocusSkill() throws Exception {
        TestUser user = onboardedOwner();

        api.put(user, GOAL, request("2027-04-01", List.of("JAVA.EXCEPTION", "NOPE"), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("focusSkillCodes[1]"))
                .andExpect(jsonPath("$.errors[0].code").value("SKILL_CODE_UNKNOWN"));
    }

    @Test
    void shouldRejectDuplicateFocusSkills() throws Exception {
        TestUser user = onboardedOwner();

        api.put(user, GOAL, request("2027-04-01", List.of("JAVA.EXCEPTION", "JAVA.EXCEPTION"), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("focusSkillCodes"))
                .andExpect(jsonPath("$.errors[0].code").value("UniqueElements"));
    }

    @Test
    void shouldRequireOnboardingBeforeGoalAccess() throws Exception {
        TestUser user = TestUser.owner();

        api.get(user, GOAL)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
        api.put(user, GOAL, request("2027-04-01", List.of(), 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    private static Map<String, Object> request(String target, List<String> focus, long version) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetRole", "JAVA_BACKEND");
        request.put("targetCompletionDate", target);
        request.put("focusSkillCodes", focus);
        request.put("version", version);
        return request;
    }
}

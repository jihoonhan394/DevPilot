package com.devpilot.onboarding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** docs/05 §4.1 (BL-GOL-06), AC-11 S1~S4·S6. 테스트 catalog: JAVA_BACKEND role target 10개. */
@IntegrationTest
class OnboardingServiceIntegrationTest extends ApiTestSupport {

    private static final String ONBOARDING = "/api/v1/onboarding";

    @Test
    void shouldPersistEverythingInOneStepWhenSelfAssessmentMode() throws Exception {
        TestUser user = TestUser.owner();

        JsonNode body =
                api.body(
                        api.post(user, ONBOARDING, TestApi.onboardingRequest())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.user.onboardingCompleted").value(true))
                                .andExpect(jsonPath("$.user.displayName").value("Test Owner"))
                                .andExpect(jsonPath("$.user.weekdayStudyMinutes").value(45))
                                .andExpect(
                                        jsonPath("$.learningGoal.targetRole").value("JAVA_BACKEND"))
                                .andExpect(jsonPath("$.learningGoal.checkpointDate").doesNotExist())
                                .andExpect(
                                        jsonPath("$.learningGoal.targetCompletionDate")
                                                .value("2027-04-01"))
                                .andExpect(
                                        jsonPath("$.learningGoal.focusSkills[0].code")
                                                .value("SPRING.TRANSACTION"))
                                .andExpect(jsonPath("$.activePlan.planVersion").value(1))
                                .andExpect(jsonPath("$.activePlan.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.activePlan.milestoneCount").value(3))
                                .andExpect(jsonPath("$.activePlan.latestRiskLevel").doesNotExist())
                                .andExpect(jsonPath("$.activePlan.latestRatioBp").doesNotExist())
                                .andExpect(jsonPath("$.sideProject").doesNotExist())
                                .andExpect(jsonPath("$.assignedSeedCardCount").value(0))
                                .andExpect(jsonPath("$.suggestedDiagnostics").isEmpty()));
        UUID userId = userId(user);

        assertThat(body.path("user").path("onboardingCompletedAt").asString())
                .isEqualTo("2026-10-05T10:00:00Z");
        assertThat(count("select count(*) from devpilot.learning_goal where user_id = ?", userId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ? and"
                                        + " plan_version = 1 and status = 'ACTIVE'",
                                userId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_skill_target t join"
                                        + " devpilot.learning_plan p on p.id = t.plan_id where"
                                        + " p.user_id = ? and t.adjustment = 'ROLE_DEFAULT'",
                                userId))
                .isEqualTo(
                        count(
                                "select count(*) from devpilot.role_skill_target where target_role"
                                        + " = 'JAVA_BACKEND'"));
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_milestone m join"
                                        + " devpilot.learning_plan p on p.id = m.plan_id where"
                                        + " p.user_id = ?",
                                userId))
                .isEqualTo(3);
    }

    @Test
    void shouldCreateSkillStatesWithCategorySelfAssessment() throws Exception {
        TestUser user = TestUser.owner();
        api.onboard(user);
        UUID userId = userId(user);

        assertThat(
                        count(
                                "select count(*) from devpilot.user_skill_state where user_id = ?",
                                userId))
                .isEqualTo(
                        count(
                                "select count(*) from devpilot.skill s join"
                                        + " devpilot.role_skill_target t on t.skill_id = s.id where"
                                        + " t.target_role = 'JAVA_BACKEND' and s.active and"
                                        + " s.parent_id is not null"));
        assertThat(
                        count(
                                "select count(*) from devpilot.user_skill_state where user_id = ?"
                                        + " and (knowledge_level + implementation_level +"
                                        + " explanation_level + debugging_level) <> 0",
                                userId))
                .isZero();
        assertThat(selfAssessed(userId, "JAVA.EXCEPTION")).isEqualTo(3);
        assertThat(selfAssessed(userId, "JAVA.COLLECTION")).isEqualTo(3);
        assertThat(selfAssessed(userId, "SPRING.TRANSACTION")).isEqualTo(2);
        assertThat(selfAssessed(userId, "TESTING.JUNIT")).isNull();
    }

    @Test
    void shouldExposePlanningLevelCappedAtThreeWhenSelfAssessed() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put(
                "selfAssessments",
                List.of(
                        Map.of("category", "JAVA", "level", 5),
                        Map.of("category", "SPRING", "level", 2)));
        api.onboard(user, request);

        JsonNode states = api.body(api.get(user, "/api/v1/skills/me")).path("items");

        JsonNode java = find(states, "JAVA.EXCEPTION");
        assertThat(java.path("selfAssessedLevel").asInt()).isEqualTo(5);
        assertThat(java.path("planningLevels").path("knowledge").asInt()).isEqualTo(3);
        assertThat(java.path("planningLevels").path("debugging").asInt()).isEqualTo(3);
        assertThat(java.path("evidenceLevels").path("knowledge").asInt()).isZero();
        assertThat(
                        find(states, "SPRING.TRANSACTION")
                                .path("planningLevels")
                                .path("implementation")
                                .asInt())
                .isEqualTo(2);
        assertThat(find(states, "TESTING.JUNIT").path("planningLevels").path("knowledge").asInt())
                .isZero();
    }

    @Test
    void shouldCreateSideProjectWhenRequested() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("sideProject", TestApi.sideProjectRequest("주문 서비스"));

        api.post(user, ONBOARDING, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sideProject.name").value("주문 서비스"))
                .andExpect(jsonPath("$.sideProject.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sideProject.version").value(0));

        assertThat(
                        count(
                                "select count(*) from devpilot.side_project where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldLeaveNothingWhenFocusSkillIsUnknown() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        learningGoal(request).put("focusSkillCodes", List.of("NO.SUCH_SKILL"));

        api.post(user, ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("learningGoal.focusSkillCodes[0]"))
                .andExpect(jsonPath("$.errors[0].code").value("SKILL_CODE_UNKNOWN"));

        assertNothingPersisted(user);
    }

    @Test
    void shouldRejectDuplicateCategory() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put(
                "selfAssessments",
                List.of(
                        Map.of("category", "JAVA", "level", 3),
                        Map.of("category", "JAVA", "level", 1)));

        api.post(user, ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("selfAssessments[1].category"))
                .andExpect(jsonPath("$.errors[0].code").value("DUPLICATE_VALUE"));
        assertNothingPersisted(user);
    }

    @Test
    void shouldRejectMoreThanThirteenSelfAssessments() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        List<Object> assessments = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            assessments.add(Map.of("category", "JAVA", "level", 1));
        }
        request.put("selfAssessments", assessments);

        api.post(user, ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("selfAssessments"))
                .andExpect(jsonPath("$.errors[0].code").value("Size"));
    }

    @Test
    void shouldRejectSelfAssessmentsInDiagnosticMode() throws Exception {
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("runDiagnostic", true);

        api.post(TestUser.owner(), ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("selfAssessments"))
                .andExpect(jsonPath("$.errors[0].code").value("MUTUALLY_EXCLUSIVE"));
    }

    @Test
    void shouldRequireSelfAssessmentsWhenDiagnosticIsSkipped() throws Exception {
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("selfAssessments", List.of());

        api.post(TestUser.owner(), ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("selfAssessments"))
                .andExpect(jsonPath("$.errors[0].code").value("ONE_OF_REQUIRED"));
    }

    @Test
    void shouldStartWithNullSelfAssessmentWhenDiagnosticMode() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("runDiagnostic", true);
        request.put("selfAssessments", List.of());

        api.post(user, ONBOARDING, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedDiagnostics").isEmpty());

        assertThat(
                        count(
                                "select count(*) from devpilot.user_skill_state where user_id = ?"
                                        + " and self_assessed_level is not null",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldRejectInvalidTimezoneAndTargetDateBeyondThreeYears() throws Exception {
        Map<String, Object> request = TestApi.onboardingRequest();
        request.put("timezone", "Mars/Olympus");
        learningGoal(request).put("targetCompletionDate", "2029-10-06");

        api.post(TestUser.owner(), ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'timezone')].code")
                                .value("TIMEZONE_INVALID"))
                .andExpect(
                        jsonPath(
                                        "$.errors[?(@.field =="
                                                + " 'learningGoal.targetCompletionDate')].code")
                                .value("DATE_OUT_OF_RANGE"));
    }

    @Test
    void shouldRejectPropertiesOutsideGoalModel() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> withProfile = TestApi.onboardingRequest();
        withProfile.put("experienceProfile", "WORKING_DEVELOPER");
        Map<String, Object> withCheckpoint = TestApi.onboardingRequest();
        learningGoal(withCheckpoint).put("checkpointDate", "2027-01-05");

        api.post(user, ONBOARDING, withProfile)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        api.post(user, ONBOARDING, withCheckpoint)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertNothingPersisted(user);
    }

    @Test
    void shouldRejectTargetDateNotAfterToday() throws Exception {
        Map<String, Object> request = TestApi.onboardingRequest();
        learningGoal(request).put("targetCompletionDate", "2026-10-05");

        api.post(TestUser.owner(), ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("learningGoal.targetCompletionDate"))
                .andExpect(jsonPath("$.errors[0].code").value("DATE_OUT_OF_RANGE"));
    }

    @Test
    void shouldRejectInvalidSideProjectUrl() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        Map<String, Object> sideProject = TestApi.sideProjectRequest("주문 서비스");
        sideProject.put("repoUrl", "ftp://repo.example.invalid/x");
        request.put("sideProject", sideProject);

        api.post(user, ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sideProject.repoUrl"))
                .andExpect(jsonPath("$.errors[0].code").value("URL"));
        assertNothingPersisted(user);
    }

    @Test
    void shouldRejectUnknownEnumValue() throws Exception {
        Map<String, Object> request = TestApi.onboardingRequest();
        learningGoal(request).put("targetRole", "java_backend");

        api.post(TestUser.owner(), ONBOARDING, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_ENUM_VALUE"));
    }

    @Test
    void shouldRejectSecondOnboarding() throws Exception {
        TestUser user = onboardedOwner();

        api.post(user, ONBOARDING, TestApi.onboardingRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_goal where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldReplaySameResponseWhenKeyIsReused() throws Exception {
        TestUser user = TestUser.owner();
        String key = TestApi.newKey();
        String first =
                api.postWithKey(user, key, ONBOARDING, TestApi.onboardingRequest())
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String replay =
                api.postWithKey(user, key, ONBOARDING, TestApi.onboardingRequest())
                        .andExpect(status().isCreated())
                        .andExpect(header().string("Idempotent-Replayed", "true"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(jsonMapper.readTree(replay)).isEqualTo(jsonMapper.readTree(first));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_goal where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldRequireIdempotencyKey() throws Exception {
        TestUser user = TestUser.owner();

        mockMvc.perform(
                        post(ONBOARDING)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(api.toJson(TestApi.onboardingRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void shouldCreateOnlyOneGoalWhenTwoOnboardingsRace() throws Exception {
        TestUser user = TestUser.owner();
        api.get(user, "/api/v1/me");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                Callable<Integer> call =
                        () -> {
                            start.await();
                            return api.post(user, ONBOARDING, TestApi.onboardingRequest())
                                    .andReturn()
                                    .getResponse()
                                    .getStatus();
                        };
                futures.add(executor.submit(call));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_goal where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    private void assertNothingPersisted(TestUser user) {
        UUID userId = userId(user);
        if (userId == null) {
            return;
        }
        assertThat(count("select count(*) from devpilot.learning_goal where user_id = ?", userId))
                .isZero();
        assertThat(count("select count(*) from devpilot.learning_plan where user_id = ?", userId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.user_skill_state where user_id = ?",
                                userId))
                .isZero();
        assertThat(count("select count(*) from devpilot.side_project where user_id = ?", userId))
                .isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.app_user where id = ? and"
                                        + " onboarding_completed_at is null",
                                userId))
                .isEqualTo(1);
    }

    private Integer selfAssessed(UUID userId, String skillCode) {
        return jdbc.queryForObject(
                "select st.self_assessed_level from devpilot.user_skill_state st join"
                    + " devpilot.skill s on s.id = st.skill_id where st.user_id = ? and s.code = ?",
                Integer.class,
                userId,
                skillCode);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }

    private static JsonNode find(JsonNode items, String code) {
        for (JsonNode item : items) {
            if (code.equals(item.path("skill").path("code").asString())) {
                return item;
            }
        }
        throw new AssertionError("no skill state for " + code);
    }
}

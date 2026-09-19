package com.devpilot.integration.ai.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §1.11 적용 endpoint (docs/07 §8.2, ST-29, AC-14 S1): private key가 한 필드라도 있으면 422 {@code
 * SECRET_DETECTED_BLOCKED}, 저장 없음, 감사 {@code SECRET_BLOCKED}(source별). 일반 secret은 마스킹본만 저장·응답한다. 가짜
 * secret은 런타임에 조합한다(docs/07 §11.3).
 */
@IntegrationTest
class SecretMaskingEndpointsTest extends ApiTestSupport {

    private static final String PRIVATE_KEY = "-----BEGIN " + "RSA PRIVATE KEY-----\nMIIEow";
    private static final String AWS_KEY = "AKIA" + "IOSFODNN7EXAMPLE";
    private static final String AWS_MASKED = "[REDACTED:AWS_ACCESS_KEY]";
    private static final String SESSIONS = "/api/v1/learning-sessions";
    private static final String SIDE_PROJECTS = "/api/v1/side-projects";
    private static final String REPLAN = "/api/v1/plans/{planId}/replan";
    private static final String MILESTONE = "/api/v1/plans/{planId}/milestones/{milestoneId}";

    @Test
    void shouldBlockPrivateKeyInSelfReflectionAndKeepSessionInProgress() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(20));

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.post(
                            user,
                            SESSIONS + "/{id}/complete",
                            complete(15, "회고 " + PRIVATE_KEY),
                            sessionId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "LEARNING_SESSION");
        }
        assertThat(
                        jdbc.queryForObject(
                                "select status from devpilot.learning_session where id = ?::uuid",
                                String.class,
                                sessionId))
                .isEqualTo("IN_PROGRESS");

        api.post(user, SESSIONS + "/{id}/complete", complete(15, "키 " + AWS_KEY), sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfReflection").value("키 " + AWS_MASKED));
        assertThat(
                        jdbc.queryForObject(
                                "select self_reflection from devpilot.learning_session"
                                        + " where id = ?::uuid",
                                String.class,
                                sessionId))
                .isEqualTo("키 " + AWS_MASKED);
    }

    @Test
    void shouldBlockPrivateKeyInReviewAnswerAndStoreMaskedAnswer() throws Exception {
        TestUser user = onboardedOwner();
        List<String> due = dueIds(user);
        Map<String, Object> blocked = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        blocked.put("answerText", PRIVATE_KEY);

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.post(user, "/api/v1/reviews/{reviewItemId}/answer", blocked, due.get(0))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "REVIEW_ANSWER");
        }
        assertThat(
                        count(
                                "select count(*) from devpilot.review_answer where user_id = ?",
                                userId(user)))
                .isZero();

        Map<String, Object> masked = TestApi.answerRequest("GOOD", "SELF_EXPLAIN");
        masked.put("answerText", "aws=" + AWS_KEY);
        api.post(user, "/api/v1/reviews/{reviewItemId}/answer", masked, due.get(0))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "select answer_text from devpilot.review_answer where user_id = ?",
                                String.class,
                                userId(user)))
                .isEqualTo("aws=" + AWS_MASKED);
    }

    @Test
    void shouldBlockPrivateKeyInSideProjectAndStoreMaskedFields() throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> blocked = TestApi.sideProjectRequest("주문 시스템");
        blocked.put("description", "설정 " + PRIVATE_KEY);

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.post(user, SIDE_PROJECTS, blocked)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "SIDE_PROJECT");
        }
        assertThat(sideProjects(user)).isZero();

        Map<String, Object> masked = TestApi.sideProjectRequest("주문 시스템 " + AWS_KEY);
        masked.put("stack", "Spring Boot, aws " + AWS_KEY);
        JsonNode created =
                api.body(
                        api.post(user, SIDE_PROJECTS, masked)
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("주문 시스템 " + AWS_MASKED))
                                .andExpect(
                                        jsonPath("$.stack")
                                                .value("Spring Boot, aws " + AWS_MASKED)));
        String projectId = created.path("id").asString();

        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("description", PRIVATE_KEY);
        patch.put("version", created.path("version").asLong());
        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.patch(user, SIDE_PROJECTS + "/{id}", patch, projectId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "SIDE_PROJECT");
        }
        patch.put("description", "설명 " + AWS_KEY);
        api.patch(user, SIDE_PROJECTS + "/{id}", patch, projectId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("설명 " + AWS_MASKED));
        assertThat(
                        jdbc.queryForObject(
                                "select description from devpilot.side_project where id = ?::uuid",
                                String.class,
                                projectId))
                .isEqualTo("설명 " + AWS_MASKED);
    }

    @Test
    void shouldRollBackWholeOnboardingWhenSideProjectHasPrivateKey() throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        Map<String, Object> sideProject = TestApi.sideProjectRequest("주문 시스템");
        sideProject.put("stack", PRIVATE_KEY);
        request.put("sideProject", sideProject);
        api.get(user, "/api/v1/me");

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.post(user, "/api/v1/onboarding", request)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "SIDE_PROJECT");
        }
        api.get(user, "/api/v1/me").andExpect(jsonPath("$.onboardingCompleted").value(false));
        assertThat(sideProjects(user)).isZero();
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isZero();
    }

    @Test
    void shouldBlockPrivateKeyInReplanPreviewAndCommit() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        String planId = plan.path("id").asString();
        Map<String, Object> blocked = replanRequest(plan, "사유 " + PRIVATE_KEY);

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.post(user, REPLAN + "/preview", blocked, planId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            api.post(user, REPLAN, blocked, planId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertThat(capture.events()).containsExactly("SECRET_BLOCKED", "SECRET_BLOCKED");
            assertThat(field(capture.fields(1), "source")).isEqualTo("LEARNING_PLAN");
        }
        assertThat(activePlan(user).path("id").asString()).isEqualTo(planId);

        Map<String, Object> masked = replanRequest(plan, "사유 " + AWS_KEY);
        milestones(masked).get(0).put("description", "설명 " + AWS_KEY);
        JsonNode replanned =
                api.body(api.post(user, REPLAN, masked, planId).andExpect(status().isCreated()));
        String nextPlanId = replanned.path("plan").path("id").asString();
        assertThat(
                        jdbc.queryForObject(
                                "select change_reason from devpilot.learning_plan where id ="
                                        + " ?::uuid",
                                String.class,
                                nextPlanId))
                .isEqualTo("사유 " + AWS_MASKED);
        assertThat(
                        jdbc.queryForList(
                                "select description from devpilot.plan_milestone"
                                        + " where plan_id = ?::uuid and description is not null",
                                String.class,
                                nextPlanId))
                .containsExactly("설명 " + AWS_MASKED);
    }

    @Test
    void shouldBlockPrivateKeyInMilestoneDescription() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode plan = activePlan(user);
        JsonNode milestone = plan.path("milestones").get(0);
        String planId = plan.path("id").asString();
        String milestoneId = milestone.path("id").asString();
        long version = milestone.path("version").asLong();

        try (AuditLogCapture capture = AuditLogCapture.start()) {
            api.patch(
                            user,
                            MILESTONE,
                            Map.of("description", PRIVATE_KEY, "version", version),
                            planId,
                            milestoneId)
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("SECRET_DETECTED_BLOCKED"));
            assertBlocked(capture, "LEARNING_PLAN");
        }
        api.patch(
                        user,
                        MILESTONE,
                        Map.of("description", "메모 " + AWS_KEY, "version", version),
                        planId,
                        milestoneId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("메모 " + AWS_MASKED));
    }

    @Test
    void shouldMaskBeforeLookingUpResource() throws Exception {
        // 마스킹은 조회보다 먼저다(docs/05 §1.11 순서): 없는 세션이어도 private key면 422
        TestUser user = onboardedOwner();

        api.post(
                        user,
                        SESSIONS + "/{id}/complete",
                        complete(5, PRIVATE_KEY),
                        UUID.randomUUID().toString())
                .andExpect(status().isUnprocessableContent());
        api.post(user, SESSIONS + "/{id}/complete", complete(5, "메모"), UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    private void assertBlocked(AuditLogCapture capture, String source) {
        assertThat(capture.events()).containsExactly("SECRET_BLOCKED");
        assertThat(field(capture.fields(0), "source")).isEqualTo(source);
        assertThat(field(capture.fields(0), "type")).isEqualTo("PRIVATE_KEY");
    }

    private static String field(List<KeyValuePair> pairs, String key) {
        return pairs.stream()
                .filter(pair -> key.equals(pair.key))
                .map(pair -> String.valueOf(pair.value))
                .findFirst()
                .orElse(null);
    }

    private int sideProjects(TestUser user) {
        return count(
                "select count(*) from devpilot.side_project s join devpilot.app_user u"
                        + " on u.id = s.user_id where u.external_auth_id = ?",
                user.sub());
    }

    private List<String> dueIds(TestUser user) throws Exception {
        JsonNode due = api.body(api.get(user, "/api/v1/reviews/due"));
        List<String> ids = new ArrayList<>();
        due.path("items").forEach(item -> ids.add(item.path("reviewItemId").asString()));
        return ids;
    }

    private static Map<String, Object> complete(int actualMinutes, String reflection) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actualMinutes", actualMinutes);
        request.put("selfReflection", reflection);
        return request;
    }
}

package com.devpilot.plan.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.RecordingStatementInspector;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** docs/05 §7.8, docs/06 §11.2 (BL-GOL-05), AC-01 S3·S4, AC-24 S2, docs/09 §8.2 I-02. */
@IntegrationTest
class ReplanServiceIntegrationTest extends ApiTestSupport {

    private static final String REPLAN = "/api/v1/plans/{planId}/replan";

    @Test
    void shouldCreateNewVersionAndKeepPreviousUnchanged() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "마지막에 배포 단계를 추가한다");
        milestones(request)
                .add(
                        milestone(
                                null,
                                "배포",
                                "2027-03-01",
                                "2027-03-20",
                                3,
                                List.of("DEVOPS.DOCKER")));

        JsonNode response =
                api.body(
                        api.post(user, REPLAN, request, id(v1))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.plan.planVersion").value(2))
                                .andExpect(jsonPath("$.plan.status").value("ACTIVE"))
                                .andExpect(jsonPath("$.plan.supersedesPlanId").value(id(v1)))
                                .andExpect(
                                        jsonPath("$.plan.changeReason").value("마지막에 배포 단계를 추가한다"))
                                .andExpect(jsonPath("$.plan.milestones.length()").value(4))
                                .andExpect(jsonPath("$.plan.skillTargets.length()").value(10)));

        JsonNode mapping = response.path("milestoneIdMapping");
        assertThat(mapping).hasSize(3);
        Set<String> previousIds = new HashSet<>();
        v1.path("milestones")
                .forEach(milestone -> previousIds.add(milestone.path("id").asString()));
        response.path("plan")
                .path("milestones")
                .forEach(
                        milestone ->
                                assertThat(previousIds)
                                        .doesNotContain(milestone.path("id").asString()));
        mapping.forEach(
                entry -> assertThat(previousIds).contains(entry.path("previousId").asString()));

        api.get(user, "/api/v1/plans")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[1].status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.items[1].supersededAt").value("2026-10-05T10:00:00Z"));
        JsonNode stored = api.body(api.get(user, "/api/v1/plans/{planId}", id(v1)));
        assertThat(stored.path("milestones")).isEqualTo(v1.path("milestones"));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ? and"
                                        + " status = 'ACTIVE'",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldFlushSupersedeBeforeInsertingNewPlan() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);

        RecordingStatementInspector.start();
        List<String> statements;
        try {
            api.post(user, REPLAN, replanRequest(v1, "순서 확인"), id(v1))
                    .andExpect(status().isCreated());
        } finally {
            statements = RecordingStatementInspector.stop();
        }

        int supersede = indexOf(statements, "update ", "learning_plan set ");
        int insert = indexOf(statements, "insert into ", "learning_plan (");
        assertThat(supersede).isNotNegative();
        assertThat(insert).isGreaterThan(supersede);
    }

    @Test
    void shouldAuditReplanAfterCommit() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);

        try (AuditLogCapture audit = AuditLogCapture.start()) {
            api.post(user, REPLAN, replanRequest(v1, "감사 로그"), id(v1))
                    .andExpect(status().isCreated());

            assertThat(audit.events()).containsExactly("PLAN_REPLANNED");
        }
    }

    @Test
    void shouldRejectTargetAdjustmentsInS1() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "목표 조정");
        request.put("acceptedDeferrals", List.of("SYSTEM_DESIGN.CACHING"));
        request.put(
                "acceptedTargetReductions",
                List.of(
                        Map.of(
                                "skillCode",
                                "DATABASE.INDEX",
                                "axis",
                                "DEBUGGING",
                                "newTarget",
                                1)));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedDeferrals')].code")
                                .value("VALUE_NOT_ALLOWED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetReductions')].code")
                                .value("VALUE_NOT_ALLOWED"));
        assertThat(activePlan(user).path("planVersion").asInt()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidMilestoneInputs() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "잘못된 입력");
        List<Map<String, Object>> milestones = milestones(request);
        milestones.get(0).put("skillCodes", List.of("NO.SUCH_SKILL"));
        milestones.get(1).put("id", UUID.randomUUID().toString());
        milestones.get(2).put("startDate", "2027-04-02");
        milestones.add(
                milestone(
                        id(v1.path("milestones").get(0)),
                        "중복",
                        "2026-10-10",
                        "2031-01-01",
                        4,
                        List.of()));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'milestones[0].skillCodes[0]')].code")
                                .value("SKILL_CODE_UNKNOWN"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'milestones[1].id')].code")
                                .value("MILESTONE_NOT_IN_PLAN"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'milestones[2].endDate')].code")
                                .value("DATE_ORDER_INVALID"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'milestones[3].id')].code")
                                .value("DUPLICATE_VALUE"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'milestones[3].endDate')].code")
                                .value("DATE_OUT_OF_RANGE"));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldRejectBlankReasonAndMissingLists() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, " ");
        request.remove("restoredDeferrals");

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'reason')].code").value("NotBlank"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'restoredDeferrals')].code")
                                .value("NotNull"));
    }

    @Test
    void shouldRejectStaleVersionAndSupersededPlan() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> stale = replanRequest(v1, "오래된 version");
        stale.put("version", 5);

        api.post(user, REPLAN, stale, id(v1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        api.post(user, REPLAN, replanRequest(v1, "v2"), id(v1)).andExpect(status().isCreated());
        api.post(user, REPLAN, replanRequest(v1, "다시 v1"), id(v1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_ACTIVE"));
    }

    @Test
    void shouldReplanAgainFromLatestVersionAfterConflict() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        api.post(user, REPLAN, replanRequest(v1, "v2"), id(v1)).andExpect(status().isCreated());
        JsonNode v2 = activePlan(user);

        api.post(user, REPLAN, replanRequest(v2, "v3"), id(v2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan.planVersion").value(3));

        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ? and"
                                        + " status = 'ACTIVE'",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldHideOtherUsersPlan() throws Exception {
        TestUser owner = onboardedOwner();
        TestUser other = onboardedOwner();
        JsonNode plan = activePlan(owner);

        api.post(other, REPLAN, replanRequest(plan, "남의 계획"), id(plan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
        assertThat(activePlan(owner).path("planVersion").asInt()).isEqualTo(1);
    }

    @Test
    void shouldReplayWithoutCreatingThirdVersion() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        String key = TestApi.newKey();
        api.postWithKey(user, key, REPLAN, replanRequest(v1, "재생"), id(v1))
                .andExpect(status().isCreated());

        api.postWithKey(user, key, REPLAN, replanRequest(v1, "재생"), id(v1))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.plan.planVersion").value(2));

        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(2);
    }

    @Test
    void shouldRequireIdempotencyKey() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);

        mockMvc.perform(
                        post(REPLAN, id(v1))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(api.toJson(replanRequest(v1, "키 없음"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    private static String id(JsonNode node) {
        return node.path("id").asString();
    }

    private static int indexOf(List<String> statements, String prefix, String table) {
        for (int i = 0; i < statements.size(); i++) {
            String statement = statements.get(i);
            if (statement.startsWith(prefix) && statement.contains(table)) {
                return i;
            }
        }
        return -1;
    }
}

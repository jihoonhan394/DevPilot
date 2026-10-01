package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.AuditLogCapture;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-05 Replan preview → commit → 새 version (AC-01, AC-03, AC-24). risk는 목표일까지의
 * budget과 skill 목표로 정해지므로(docs/06 §3·§4) HIGH 상태는 목표일 2026-11-09인 사용자로 만든다(effective 2467분,
 * requiredMust 2825분, 11451bp).
 */
@IntegrationTest
class ReplanFlowTest extends ApiTestSupport {

    @Test
    void shouldPreviewCommitAndRestoreAcrossVersions() throws Exception {
        // 1. 활성 plan P1
        TestUser user = TestUser.owner();
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        learningGoal(onboarding).put("targetCompletionDate", "2026-11-09");
        api.onboard(user, onboarding);
        UUID userId = userId(user);
        JsonNode p1 = activePlan(user);
        String p1Id = p1.path("id").asString();
        api.generateToday(user, 30, "NORMAL");

        // 2. 미리보기 (Idempotency-Key 없음)
        JsonNode preview =
                api.body(
                        mockMvc.perform(
                                        post("/api/v1/plans/{planId}/replan/preview", p1Id)
                                                .header(
                                                        HttpHeaders.AUTHORIZATION,
                                                        "Bearer " + api.token(user))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(api.toJson(replanRequest(p1, "미리보기"))))
                                .andExpect(status().isOk()));
        assertThat(preview.path("riskLevel").asString()).isEqualTo("HIGH");
        assertThat(preview.path("deferSuggestions")).isNotEmpty();
        assertThat(preview.path("riskAfterSuggestions").isObject()).isTrue();
        assertThat(count("select count(*) from devpilot.learning_plan where user_id = ?", userId))
                .isEqualTo(1);
        String deferred =
                preview.path("deferSuggestions").get(0).path("skill").path("code").asString();

        // 3. 첫 제안 채택
        Map<String, Object> commit = replanRequest(p1, "기한에 맞춰 미룬다");
        commit.put("acceptedDeferrals", List.of(deferred));
        JsonNode p2;
        try (AuditLogCapture audit = AuditLogCapture.start()) {
            p2 =
                    api.body(
                                    api.post(user, "/api/v1/plans/{planId}/replan", commit, p1Id)
                                            .andExpect(status().isCreated())
                                            .andExpect(jsonPath("$.plan.planVersion").value(2))
                                            .andExpect(
                                                    jsonPath("$.plan.supersedesPlanId")
                                                            .value(p1Id)))
                            .path("plan");
            assertThat(audit.events()).contains("PLAN_REPLANNED");
        }
        String p2Id = p2.path("id").asString();

        // 4. 조회
        assertThat(activePlan(user).path("id").asString()).isEqualTo(p2Id);
        api.get(user, "/api/v1/plans/{planId}", p1Id)
                .andExpect(jsonPath("$.status").value("SUPERSEDED"))
                .andExpect(jsonPath("$.supersededAt").isString());
        api.get(user, "/api/v1/plans").andExpect(jsonPath("$.items.length()").value(2));

        // 5. DB
        Map<String, Object> target = target(p2Id, deferred);
        assertThat(target).containsEntry("deferred", true).containsEntry("adjustment", "DEFERRED");
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                    + " event_type = 'PLAN_REPLANNED' and payload->>'fromVersion' ="
                                    + " '1' and payload->>'toVersion' = '2'",
                                userId))
                .isEqualTo(1);
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_progress_snapshot where plan_id"
                                        + " = ?::uuid and snapshot_date = date '2026-10-05'",
                                p2Id))
                .isEqualTo(1);

        // 6. 지난 daily_plan은 P1을 가리킨다
        assertThat(
                        jdbc.queryForObject(
                                "select learning_plan_id::text from devpilot.daily_plan where"
                                        + " user_id = ?",
                                String.class,
                                userId))
                .isEqualTo(p1Id);

        // 7. P1 다시 replan
        api.post(user, "/api/v1/plans/{planId}/replan", replanRequest(p1, "옛 버전"), p1Id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_ACTIVE"));

        // 8. milestone version 충돌
        String milestoneId = p2.path("milestones").get(0).path("id").asString();
        String milestonePath = "/api/v1/plans/{planId}/milestones/{milestoneId}";
        api.patch(
                        user,
                        milestonePath,
                        Map.of("status", "IN_PROGRESS", "version", 0),
                        p2Id,
                        milestoneId)
                .andExpect(status().isOk());
        api.patch(user, milestonePath, Map.of("status", "DONE", "version", 0), p2Id, milestoneId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));

        // 8a. defer와 복원을 동시에
        JsonNode current = activePlan(user);
        Map<String, Object> conflicting = replanRequest(current, "동시 요청");
        conflicting.put("acceptedDeferrals", List.of(deferred));
        conflicting.put("restoredDeferrals", List.of(deferred));
        api.post(user, "/api/v1/plans/{planId}/replan", conflicting, p2Id)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(count("select count(*) from devpilot.learning_plan where user_id = ?", userId))
                .isEqualTo(2);

        // 8b. 복원
        Map<String, Object> restore = replanRequest(current, "다시 포함한다");
        restore.put("restoredDeferrals", List.of(deferred));
        JsonNode p3 =
                api.body(
                                api.post(user, "/api/v1/plans/{planId}/replan", restore, p2Id)
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.plan.planVersion").value(3)))
                        .path("plan");
        assertThat(target(p3.path("id").asString(), deferred))
                .containsEntry("deferred", false)
                .containsEntry("adjustment", "USER_EDITED");
    }

    private Map<String, Object> target(String planId, String skillCode) {
        return jdbc.queryForMap(
                "select t.deferred, t.adjustment from devpilot.plan_skill_target t join"
                        + " devpilot.skill s on s.id = t.skill_id where t.plan_id = ?::uuid and"
                        + " s.code = ?",
                planId,
                skillCode);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> learningGoal(Map<String, Object> request) {
        return (Map<String, Object>) request.get("learningGoal");
    }
}

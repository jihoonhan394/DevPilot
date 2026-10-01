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
    void shouldApplyDeferralAndReductionAndRecordReplannedEvent() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "목표 조정");
        request.put("acceptedDeferrals", List.of("SYSTEM_DESIGN.CACHING"));
        request.put("acceptedTargetReductions", List.of(change("DATABASE.INDEX", "DEBUGGING", 1)));

        api.post(user, REPLAN, request, id(v1)).andExpect(status().isCreated());

        JsonNode v2 = activePlan(user);
        JsonNode caching = target(v2, "SYSTEM_DESIGN.CACHING");
        assertThat(caching.path("deferred").asBoolean()).isTrue();
        assertThat(caching.path("adjustment").asString()).isEqualTo("DEFERRED");
        JsonNode index = target(v2, "DATABASE.INDEX");
        assertThat(index.path("targets").path("debugging").asInt()).isEqualTo(1);
        assertThat(index.path("targets").path("knowledge").asInt()).isEqualTo(4);
        assertThat(index.path("adjustment").asString()).isEqualTo("TARGET_REDUCED");
        assertThat(target(v2, "JAVA.EXCEPTION").path("adjustment").asString())
                .isEqualTo("ROLE_DEFAULT");

        UUID userId = userId(user);
        Map<String, Object> event =
                jdbc.queryForMap(
                        "select source_id::text as source_id, plan_date::text as plan_date,"
                            + " payload->>'fromVersion' as from_version, payload->>'toVersion' as"
                            + " to_version, payload->'deferredSkillCodes' ->> 0 as deferred,"
                            + " payload->'reducedSkillCodes' ->> 0 as reduced from"
                            + " devpilot.learning_event where user_id = ? and event_type ="
                            + " 'PLAN_REPLANNED'",
                        userId);
        assertThat(event)
                .containsEntry("source_id", id(v2))
                .containsEntry("plan_date", "2026-10-05")
                .containsEntry("from_version", "1")
                .containsEntry("to_version", "2")
                .containsEntry("deferred", "SYSTEM_DESIGN.CACHING")
                .containsEntry("reduced", "DATABASE.INDEX");
        // 새 plan 기준 오늘 snapshot (docs/05 §7.8, BL-GOL-13)
        assertThat(
                        count(
                                "select count(*) from devpilot.plan_progress_snapshot where plan_id"
                                        + " = ?::uuid and snapshot_date = date '2026-10-05'",
                                id(v2)))
                .isEqualTo(1);
        assertThat(v2.path("latestSnapshot").path("snapshotDate").asString())
                .isEqualTo("2026-10-05");
    }

    @Test
    void shouldRestoreDeferredSkillAsUserEdited() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> defer = replanRequest(v1, "미루기");
        defer.put("acceptedDeferrals", List.of("SYSTEM_DESIGN.CACHING"));
        api.post(user, REPLAN, defer, id(v1)).andExpect(status().isCreated());
        JsonNode v2 = activePlan(user);

        Map<String, Object> restore = replanRequest(v2, "되돌리기");
        restore.put("restoredDeferrals", List.of("SYSTEM_DESIGN.CACHING"));
        api.post(user, REPLAN, restore, id(v2)).andExpect(status().isCreated());

        JsonNode caching = target(activePlan(user), "SYSTEM_DESIGN.CACHING");
        assertThat(caching.path("deferred").asBoolean()).isFalse();
        assertThat(caching.path("adjustment").asString()).isEqualTo("USER_EDITED");
    }

    @Test
    void shouldRejectSameSkillInDeferralsAndRestorations() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "동시 요청");
        request.put("acceptedDeferrals", List.of("SYSTEM_DESIGN.CACHING"));
        request.put("restoredDeferrals", List.of("SYSTEM_DESIGN.CACHING"));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'restoredDeferrals[0]')].code")
                                .value("MUTUALLY_EXCLUSIVE"));
        assertThat(activePlan(user).path("planVersion").asInt()).isEqualTo(1);
    }

    @Test
    void shouldRejectReductionThatDoesNotLowerTarget() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "축소 아님");
        request.put("acceptedTargetReductions", List.of(change("DATABASE.INDEX", "DEBUGGING", 2)));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath(
                                        "$.errors[?(@.field =="
                                                + " 'acceptedTargetReductions[0].newTarget')].code")
                                .value("TARGET_NOT_REDUCED"));
    }

    @Test
    void shouldRaiseTargetAsUserEdited() throws Exception {
        // RX-7
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "목표 상향");
        request.put("acceptedTargetRaises", List.of(change("TESTING.JUNIT", "KNOWLEDGE", 4)));

        api.post(user, REPLAN, request, id(v1)).andExpect(status().isCreated());

        JsonNode v2 = activePlan(user);
        JsonNode junit = target(v2, "TESTING.JUNIT");
        assertThat(junit.path("targets").path("knowledge").asInt()).isEqualTo(4);
        assertThat(junit.path("targets").path("implementation").asInt()).isEqualTo(4);
        assertThat(junit.path("adjustment").asString()).isEqualTo("USER_EDITED");
        assertThat(target(v2, "DATABASE.INDEX").path("targets"))
                .isEqualTo(target(v1, "DATABASE.INDEX").path("targets"));
    }

    @Test
    void shouldApplyReductionAndRaiseOnDifferentAxesAsReduced() throws Exception {
        // RX-8
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "축소와 상향");
        request.put("acceptedTargetReductions", List.of(change("DATABASE.INDEX", "DEBUGGING", 1)));
        request.put("acceptedTargetRaises", List.of(change("DATABASE.INDEX", "KNOWLEDGE", 5)));

        api.post(user, REPLAN, request, id(v1)).andExpect(status().isCreated());

        JsonNode index = target(activePlan(user), "DATABASE.INDEX");
        assertThat(index.path("targets").path("debugging").asInt()).isEqualTo(1);
        assertThat(index.path("targets").path("knowledge").asInt()).isEqualTo(5);
        assertThat(index.path("adjustment").asString()).isEqualTo("TARGET_REDUCED");
    }

    @Test
    void shouldRejectInvalidTargetRaises() throws Exception {
        // RX-1, RX-3~RX-6. RX-2(newTarget 6)는 shouldRejectRaiseAboveFive
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "잘못된 상향");
        request.put("acceptedDeferrals", List.of("DEVOPS.DOCKER"));
        request.put("acceptedTargetReductions", List.of(change("DATABASE.INDEX", "DEBUGGING", 1)));
        request.put(
                "acceptedTargetRaises",
                List.of(
                        change("TESTING.JUNIT", "KNOWLEDGE", 3),
                        change("DATABASE.INDEX", "DEBUGGING", 3),
                        change("DEVOPS.DOCKER", "KNOWLEDGE", 4),
                        change("JAVA.EXCEPTION", "DEBUGGING", 4),
                        change("JAVA.EXCEPTION", "DEBUGGING", 5),
                        change("JAVA", "KNOWLEDGE", 4),
                        change("NO.SUCH_SKILL", "KNOWLEDGE", 4)));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[0].newTarget')].code")
                                .value("TARGET_NOT_RAISED"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[1].axis')].code")
                                .value("MUTUALLY_EXCLUSIVE"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[2].skillCode')].code")
                                .value("MUTUALLY_EXCLUSIVE"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[4].axis')].code")
                                .value("DUPLICATE_VALUE"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[5].skillCode')].code")
                                .value("SKILL_NOT_IN_PLAN"))
                .andExpect(
                        jsonPath("$.errors[?(@.field == 'acceptedTargetRaises[6].skillCode')].code")
                                .value("SKILL_CODE_UNKNOWN"));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_plan where user_id = ?",
                                userId(user)))
                .isEqualTo(1);
        assertThat(target(activePlan(user), "DEVOPS.DOCKER").path("deferred").asBoolean())
                .isFalse();
    }

    @Test
    void shouldRejectRaiseAboveFive() throws Exception {
        // RX-2: @Max(5) (docs/05 §7.8 TargetRaiseInput)
        TestUser user = onboardedOwner();
        JsonNode v1 = activePlan(user);
        Map<String, Object> request = replanRequest(v1, "상한 초과");
        request.put("acceptedTargetRaises", List.of(change("TESTING.JUNIT", "KNOWLEDGE", 6)));

        api.post(user, REPLAN, request, id(v1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(
                        jsonPath("$.errors[0].field").value("acceptedTargetRaises[0].newTarget"));
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

    private static Map<String, Object> change(String skillCode, String axis, int newTarget) {
        return Map.of("skillCode", skillCode, "axis", axis, "newTarget", newTarget);
    }

    private static JsonNode target(JsonNode plan, String skillCode) {
        for (JsonNode target : plan.path("skillTargets")) {
            if (skillCode.equals(target.path("skill").path("code").asString())) {
                return target;
            }
        }
        throw new AssertionError("no target for " + skillCode);
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

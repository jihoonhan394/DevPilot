package com.devpilot.learning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §9.1~§9.4 (BL-TDY-10), AC-02 S9, AC-17 S2, docs/04 §6 {@code SESSION_*} 이벤트 (BL-SKL-03).
 * 기본 시계 2026-10-05T10:00:00Z (KST 19:00, plan-day 2026-10-05).
 */
@IntegrationTest
class LearningSessionServiceIntegrationTest extends ApiTestSupport {

    private static final String SESSIONS = "/api/v1/learning-sessions";

    @Test
    void shouldStartSessionForMainTaskAndRecordEvent() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode main = api.generateToday(user, 30, "NORMAL").path("mainTask");
        String taskId = main.path("id").asString();

        JsonNode started = api.startSession(user, taskId);

        JsonNode session = started.path("session");
        assertThat(session.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(session.path("learningTaskId").asString()).isEqualTo(taskId);
        assertThat(session.path("planDate").asString()).isEqualTo("2026-10-05");
        assertThat(session.path("startedAt").asString()).isEqualTo("2026-10-05T10:00:00Z");
        assertThat(started.path("abandonedSessionId").isNull()).isTrue();
        UUID userId = userId(user);
        assertThat(
                        jdbc.queryForList(
                                "select e.event_type from devpilot.learning_event e join"
                                        + " devpilot.learning_task t on t.skill_id = e.skill_id"
                                        + " where e.user_id = ? and t.id = ?::uuid and"
                                        + " e.session_id = ?::uuid and e.plan_date ="
                                        + " date '2026-10-05'",
                                String.class,
                                userId,
                                taskId,
                                session.path("id").asString()))
                .containsExactly("SESSION_STARTED");
        // docs/05 §9.1 6단계: PLANNED task는 세션 시작과 함께 IN_PROGRESS
        api.get(user, "/api/v1/today")
                .andExpect(jsonPath("$.mainTask.status").value("IN_PROGRESS"));
    }

    @Test
    void shouldAbandonPreviousSessionWhenAnotherStarts() throws Exception {
        TestUser user = onboardedOwner();
        String first = api.startSession(user, null).path("session").path("id").asString();

        JsonNode second = api.startSession(user, null);

        assertThat(second.path("abandonedSessionId").asString()).isEqualTo(first);
        UUID userId = userId(user);
        assertThat(
                        jdbc.queryForList(
                                "select status from devpilot.learning_session where user_id = ?"
                                        + " order by started_at, status",
                                String.class,
                                userId))
                .containsExactlyInAnyOrder("ABANDONED", "IN_PROGRESS");
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_session where user_id = ?"
                                        + " and status = 'IN_PROGRESS'",
                                userId))
                .isEqualTo(1);
    }

    @Test
    void shouldCompleteSessionWithActualMinutesAndKeepTaskStatus() throws Exception {
        TestUser user = onboardedOwner();
        String taskId =
                api.generateToday(user, 30, "NORMAL").path("mainTask").path("id").asString();
        String sessionId = api.startSession(user, taskId).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(20));

        api.post(user, SESSIONS + "/{id}/complete", complete(25, "경계가 헷갈렸다."), sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualMinutes").value(25))
                .andExpect(jsonPath("$.selfReflection").value("경계가 헷갈렸다."))
                .andExpect(jsonPath("$.completedAt").value("2026-10-05T10:20:00Z"))
                .andExpect(jsonPath("$.planDate").value("2026-10-05"));

        Map<String, Object> event =
                jdbc.queryForMap(
                        "select plan_date::text as plan_date, payload->>'actualMinutes' as"
                                + " minutes, payload->>'taskId' as task_id from"
                                + " devpilot.learning_event where user_id = ? and event_type ="
                                + " 'SESSION_COMPLETED'",
                        userId(user));
        assertThat(event)
                .containsEntry("plan_date", "2026-10-05")
                .containsEntry("minutes", "25")
                .containsEntry("task_id", taskId);
        // docs/05 §9.2: task 상태는 바꾸지 않는다
        api.get(user, "/api/v1/today")
                .andExpect(jsonPath("$.mainTask.status").value("IN_PROGRESS"));
    }

    @Test
    void shouldStoreEmptyReflectionAsNull() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(10));

        api.post(user, SESSIONS + "/{id}/complete", complete(10, ""), sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selfReflection").doesNotExist());
    }

    @Test
    void shouldRejectActualMinutesOutOfRangeWithoutChangingSession() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofHours(10));

        api.post(user, SESSIONS + "/{id}/complete", complete(721, null), sessionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("actualMinutes"));
        api.post(user, SESSIONS + "/{id}/complete", Map.of("selfReflection", "분 없음"), sessionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("actualMinutes"));

        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldLimitActualMinutesByElapsedTime() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(10));

        // limit = ceilDiv(600 × 3, 120) = 15
        api.post(user, SESSIONS + "/{id}/complete", complete(16, null), sessionId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("actualMinutes"))
                .andExpect(jsonPath("$.errors[0].code").value("ACTUAL_MINUTES_EXCEEDS_ELAPSED"));
        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");

        api.post(user, SESSIONS + "/{id}/complete", complete(15, null), sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualMinutes").value(15));
    }

    @Test
    void shouldRejectTransitionsFromFinishedSession() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(5));
        api.post(user, SESSIONS + "/{id}/complete", complete(5, null), sessionId)
                .andExpect(status().isOk());

        api.post(user, SESSIONS + "/{id}/complete", complete(5, null), sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        abandon(user, sessionId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void shouldAbandonInProgressSessionWithoutEvent() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();

        abandon(user, sessionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABANDONED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.actualMinutes").doesNotExist());

        assertThat(
                        jdbc.queryForList(
                                "select event_type from devpilot.learning_event where user_id = ?",
                                String.class,
                                userId(user)))
                .containsExactly("SESSION_STARTED");
    }

    @Test
    void shouldRejectOtherUsersTaskAsUnknownReference() throws Exception {
        TestUser owner = onboardedOwner();
        String ownerTask =
                api.generateToday(owner, 30, "NORMAL").path("mainTask").path("id").asString();
        TestUser other = onboardedOwner();

        api.post(other, SESSIONS, Map.of("learningTaskId", ownerTask))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("learningTaskId"))
                .andExpect(jsonPath("$.errors[0].code").value("REFERENCE_NOT_FOUND"));
        api.post(other, SESSIONS, Map.of("learningTaskId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("REFERENCE_NOT_FOUND"));

        assertThat(
                        count(
                                "select count(*) from devpilot.learning_session where user_id = ?",
                                userId(other)))
                .isZero();
        api.get(owner, "/api/v1/today").andExpect(jsonPath("$.mainTask.status").value("PLANNED"));
    }

    @Test
    void shouldHideOtherUsersSession() throws Exception {
        TestUser owner = onboardedOwner();
        String sessionId = api.startSession(owner, null).path("session").path("id").asString();
        TestUser other = onboardedOwner();
        clock.advance(Duration.ofMinutes(10));

        api.post(other, SESSIONS + "/{id}/complete", complete(5, null), sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        abandon(other, sessionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        assertThat(sessionStatus(sessionId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void shouldUsePreviousPlanDayAfterMidnightBeforeDayStart() throws Exception {
        TestUser user = onboardedOwner();
        clock.setInstant(Instant.parse("2026-10-05T15:40:00Z"));

        api.startSession(user, null);

        assertThat(
                        jdbc.queryForObject(
                                "select plan_date::text from devpilot.learning_session where"
                                        + " user_id = ?",
                                String.class,
                                userId(user)))
                .isEqualTo("2026-10-05");
    }

    @Test
    void shouldListSessionsNewestFirstWithinPlanDateRange() throws Exception {
        TestUser user = onboardedOwner();
        String first = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofDays(1));
        String second = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofDays(1));
        String third = api.startSession(user, null).path("session").path("id").asString();

        JsonNode all = api.body(api.get(user, SESSIONS));
        assertThat(ids(all)).containsExactly(third, second, first);

        JsonNode ranged = api.body(api.get(user, SESSIONS + "?from=2026-10-06&to=2026-10-06"));
        assertThat(ids(ranged)).containsExactly(second);

        JsonNode page = api.body(api.get(user, SESSIONS + "?limit=2"));
        assertThat(ids(page)).containsExactly(third, second);
        JsonNode next =
                api.body(
                        api.get(
                                user,
                                SESSIONS + "?limit=2&cursor={cursor}",
                                page.path("nextCursor").asString()));
        assertThat(ids(next)).containsExactly(first);
        assertThat(next.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void shouldRejectInvalidListRange() throws Exception {
        TestUser user = onboardedOwner();

        api.get(user, SESSIONS + "?from=2026-10-06&to=2026-10-05")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("to"))
                .andExpect(jsonPath("$.errors[0].code").value("DATE_ORDER_INVALID"));
        api.get(user, SESSIONS + "?from=2026-01-01&to=2027-01-02")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").value("DATE_RANGE_TOO_LONG"));
        api.get(user, SESSIONS + "?from=2026-01-01&to=2027-01-01").andExpect(status().isOk());
        api.get(user, SESSIONS + "?from=yesterday")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        api.get(user, SESSIONS + "?cursor=broken")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void shouldReplayCompletionWithSameIdempotencyKey() throws Exception {
        TestUser user = onboardedOwner();
        String sessionId = api.startSession(user, null).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(30));
        String key = TestApi.newKey();

        api.postWithKey(user, key, SESSIONS + "/{id}/complete", complete(30, null), sessionId)
                .andExpect(status().isOk());
        api.postWithKey(user, key, SESSIONS + "/{id}/complete", complete(30, null), sessionId)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ?"
                                        + " and event_type = 'SESSION_COMPLETED'",
                                userId(user)))
                .isEqualTo(1);
    }

    @Test
    void shouldRequireIdempotencyKeyToStart() throws Exception {
        TestUser user = onboardedOwner();

        mockMvc.perform(
                        post(SESSIONS)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void shouldRequireOnboardingBeforeStarting() throws Exception {
        TestUser user = TestUser.owner();

        api.post(user, SESSIONS, Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    private ResultActions abandon(TestUser user, String sessionId) throws Exception {
        return mockMvc.perform(
                post(SESSIONS + "/{id}/abandon", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.token(user)));
    }

    private String sessionStatus(String sessionId) {
        return jdbc.queryForObject(
                "select status from devpilot.learning_session where id = ?::uuid",
                String.class,
                sessionId);
    }

    private static Map<String, Object> complete(int actualMinutes, String reflection) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actualMinutes", actualMinutes);
        request.put("selfReflection", reflection);
        return request;
    }

    private static List<String> ids(JsonNode page) {
        List<String> ids = new ArrayList<>();
        page.path("items").forEach(item -> ids.add(item.path("id").asString()));
        return ids;
    }
}

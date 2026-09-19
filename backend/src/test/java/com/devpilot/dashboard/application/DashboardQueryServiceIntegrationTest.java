package com.devpilot.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §13.1 (BL-TDY-11), AC-02 S10. S2 최소판: 오늘 상태, due 수, 이번 ISO 주 학습 시간. risk 추세·timeline·카테고리
 * 요약·약한 사고 축은 S5 전까지 null/[]다.
 */
@IntegrationTest
class DashboardQueryServiceIntegrationTest extends ApiTestSupport {

    private static final String DASHBOARD = "/api/v1/dashboard";

    @Test
    void shouldSummarizeTodayDueReviewsAndWeekStudy() throws Exception {
        // AC-02 S10: S1(생성) + S9(세션 25분 완료) 뒤
        TestUser user = onboardedOwner();
        JsonNode today = api.generateToday(user, 30, "NORMAL");
        String mainId = today.path("mainTask").path("id").asString();
        String sessionId = api.startSession(user, mainId).path("session").path("id").asString();
        clock.advance(Duration.ofMinutes(20));
        api.post(
                        user,
                        "/api/v1/learning-sessions/{id}/complete",
                        Map.of("actualMinutes", 25),
                        sessionId)
                .andExpect(status().isOk());

        JsonNode dashboard = api.body(api.get(user, DASHBOARD).andExpect(status().isOk()));

        assertThat(dashboard.path("today").asString()).isEqualTo("2026-10-05");
        JsonNode summary = dashboard.path("todaySummary");
        assertThat(summary.path("generated").asBoolean()).isTrue();
        assertThat(summary.path("mainTaskId").asString()).isEqualTo(mainId);
        assertThat(summary.path("mainTaskStatus").asString()).isEqualTo("IN_PROGRESS");
        assertThat(summary.path("mainTaskTitle").asString())
                .isEqualTo(today.path("mainTask").path("title").asString());
        assertThat(summary.path("reviewTaskStatus").asString()).isEqualTo("PLANNED");
        assertThat(dashboard.path("dueReviewCount").asInt()).isEqualTo(5);
        assertThat(dashboard.path("weekStartDate").asString()).isEqualTo("2026-10-05");
        assertThat(dashboard.path("weekStudyMinutes").asInt()).isEqualTo(25);
        assertThat(dashboard.path("weekCompletedSessions").asInt()).isEqualTo(1);
        assertThat(dashboard.path("aiStatus").asString()).isEqualTo("DISABLED");
        assertThat(dashboard.path("replanRecommended").asBoolean()).isFalse();
        assertThat(dashboard.path("risk").isNull()).isTrue();
        assertThat(dashboard.path("milestoneTimeline").isNull()).isTrue();
        assertThat(dashboard.path("skillCategories")).isEmpty();
        assertThat(dashboard.path("weakThinkingAxes")).isEmpty();
    }

    @Test
    void shouldShowNotGeneratedTodayAndCountOnlyThisIsoWeek() throws Exception {
        TestUser user = onboardedOwner();
        UUID userId = userId(user);
        // 지난 주 일요일(2026-10-04) 세션은 이번 주(2026-10-05 월 ~) 합에 들어가지 않는다
        insertCompletedSession(userId, "2026-10-04", 40);
        insertCompletedSession(userId, "2026-10-05", 15);

        JsonNode dashboard = api.body(api.get(user, DASHBOARD));

        assertThat(dashboard.path("todaySummary").path("generated").asBoolean()).isFalse();
        assertThat(dashboard.path("todaySummary").path("mainTaskId").isNull()).isTrue();
        assertThat(dashboard.path("weekStudyMinutes").asInt()).isEqualTo(15);
        assertThat(dashboard.path("weekCompletedSessions").asInt()).isEqualTo(1);

        clock.setInstant(Instant.parse("2026-10-11T10:00:00Z"));
        JsonNode sunday = api.body(api.get(user, DASHBOARD));
        assertThat(sunday.path("weekStartDate").asString()).isEqualTo("2026-10-05");
        assertThat(sunday.path("weekStudyMinutes").asInt()).isEqualTo(15);
        assertThat(sunday.path("dueReviewCount").asInt()).isEqualTo(10);
    }

    @Test
    void shouldShowReplanRecommendationAfterTargetDateChange() throws Exception {
        TestUser user = onboardedOwner();
        Map<String, Object> goal =
                Map.of(
                        "targetRole",
                        "JAVA_BACKEND",
                        "targetCompletionDate",
                        "2027-06-30",
                        "focusSkillCodes",
                        List.of(),
                        "version",
                        0);
        api.put(user, "/api/v1/learning-goal", goal).andExpect(status().isOk());

        api.get(user, DASHBOARD).andExpect(jsonPath("$.replanRecommended").value(true));
    }

    @Test
    void shouldRequireOnboarding() throws Exception {
        api.get(TestUser.owner(), DASHBOARD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));
    }

    private void insertCompletedSession(UUID userId, String planDate, int minutes) {
        jdbc.update(
                "insert into devpilot.learning_session (id, user_id, plan_date, started_at,"
                        + " completed_at, actual_minutes, status) values (gen_random_uuid(), ?,"
                        + " cast(? as date), timestamptz '2026-10-04T01:00:00Z', timestamptz"
                        + " '2026-10-04T02:00:00Z', ?, 'COMPLETED')",
                userId,
                planDate,
                minutes);
    }
}

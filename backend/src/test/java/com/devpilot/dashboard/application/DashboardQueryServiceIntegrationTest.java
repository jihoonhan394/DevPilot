package com.devpilot.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.skill.domain.SkillCategory;
import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/05 §13.1 (BL-TDY-11), AC-02 S10. S2 최소판: 오늘 상태, due 수, 이번 ISO 주 학습 시간. risk 추세·timeline·카테고리
 * risk 추세와 약한 사고 축은 아직 null/[]다. 타임라인과 category 요약은 채운다.
 */
@IntegrationTest
class DashboardQueryServiceIntegrationTest extends ApiTestSupport {

    private static final String TASK = "/api/v1/today/tasks/{taskId}";

    private static final String DASHBOARD = "/api/v1/dashboard";

    /**
     * 이번 주 요약과 연속 학습 일수 (docs/05 §13.1, BL-DSH-01·02).
     *
     * <p>오늘 과제 하나를 끝내면 연속은 1이고, 만든 것 목록에는 그 과제가 들어간다. {@code notesWritten}은 프로젝트 기록(BL-PRJ-02)이 아직
     * 없어 0이다.
     */
    @Test
    void shouldCountTodaysFinishedTaskAsTheStreakAndAsSomethingBuilt() throws Exception {
        TestUser user = onboardedOwner();
        JsonNode main = api.generateToday(user, 60, "NORMAL").path("mainTask");
        String taskId = main.path("id").asString();
        int version = main.path("version").asInt();
        api.patch(user, TASK, Map.of("status", "IN_PROGRESS", "version", version), taskId)
                .andExpect(status().isOk());
        api.patch(user, TASK, Map.of("status", "COMPLETED", "version", version + 1), taskId)
                .andExpect(status().isOk());

        JsonNode dashboard = api.body(api.get(user, DASHBOARD).andExpect(status().isOk()));

        assertThat(dashboard.path("streakDays").asInt()).isEqualTo(1);
        JsonNode weekly = dashboard.path("weeklySummary");
        assertThat(weekly.path("completedTasks").asInt()).isEqualTo(1);
        assertThat(weekly.path("notesWritten").asInt()).isZero();
        // 기록을 하나 적으면 "적은 것"이 는다 — dashboard는 evidence의 지표 경로로 읽는다 (docs/03 §2.2)
        UUID projectId =
                jdbc.queryForObject(
                        "select id from devpilot.side_project where user_id = ? limit 1",
                        UUID.class,
                        userId(user));
        api.postWithKey(
                user,
                TestApi.newKey(),
                "/api/v1/side-projects/{id}/notes",
                Map.of(
                        "noteType",
                        "DECISION",
                        "title",
                        "커넥션 풀 크기를 그대로 두기로 했다",
                        "occurredOn",
                        "2026-10-05",
                        "decisionChoice",
                        "지금 값을 유지한다",
                        "decisionOptions",
                        "늘린다 / 줄인다 / 그대로",
                        "decisionRationale",
                        "실제 사용률을 아직 재 보지 않았다"),
                projectId);

        assertThat(
                        api.body(api.get(user, DASHBOARD))
                                .path("weeklySummary")
                                .path("notesWritten")
                                .asInt())
                .isEqualTo(1);
        assertThat(weekly.path("studyMinutes").asInt())
                .isEqualTo(dashboard.path("weekStudyMinutes").asInt());
    }

    /** 아무것도 끝내지 않았으면 연속은 0이고 만든 것 목록은 비어 있다 — 없는 값을 지어내지 않는다. */
    @Test
    void shouldReportZeroStreakBeforeAnythingIsFinished() throws Exception {
        TestUser user = onboardedOwner();
        api.generateToday(user, 60, "NORMAL");

        JsonNode dashboard = api.body(api.get(user, DASHBOARD).andExpect(status().isOk()));

        assertThat(dashboard.path("streakDays").asInt()).isZero();
        assertThat(dashboard.path("weeklySummary").path("builtThisWeek")).isEmpty();
        assertThat(dashboard.path("weeklySummary").path("completedTasks").asInt()).isZero();
    }

    @Test
    void shouldSummarizeTodayDueReviewsAndWeekStudy() throws Exception {
        // AC-02 S10: S1(생성) + S9(세션 25분 완료) 뒤
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 시험하려면 이미 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
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
        // GET /me와 같은 값이어야 한다 (docs/05 §13.1). test profile은 fake provider라 ENABLED다.
        assertThat(dashboard.path("aiStatus").asString())
                .isEqualTo(api.body(api.get(user, "/api/v1/me")).path("aiStatus").asString());
        assertThat(dashboard.path("replanRecommended").asBoolean()).isFalse();
        assertThat(dashboard.path("risk").isNull()).isTrue();
        // 타임라인과 category 요약은 채운다 — "어디쯤 왔나"와 "늘고 있나"에 답하는 자리다.
        assertThat(dashboard.path("milestoneTimeline").isNull()).isFalse();
        assertThat(dashboard.path("skillCategories")).isNotEmpty();
        // 코드 리뷰의 사고 축은 출처가 달라 아직 비어 있다 (docs/06 §12).
        assertThat(dashboard.path("weakThinkingAxes")).isEmpty();
    }

    @Test
    void shouldShowNotGeneratedTodayAndCountOnlyThisIsoWeek() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 시험하려면 이미 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
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
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 시험하려면 이미 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);
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

    /** docs/05 §13.1: 타임라인의 지금 단계는 날짜가 아니라 진행으로 정한다 (ADR-044). */
    @Test
    void shouldMarkTheFirstUnfinishedMilestoneAsCurrent() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 시험하려면 이미 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode timeline =
                api.body(api.get(user, DASHBOARD).andExpect(status().isOk()))
                        .path("milestoneTimeline");

        assertThat(timeline.isNull()).isFalse();
        assertThat(timeline.path("planVersion").asInt()).isPositive();
        assertThat(timeline.path("horizonDate").asString()).isNotBlank();
        JsonNode milestones = timeline.path("milestones");
        assertThat(milestones).isNotEmpty();

        List<Integer> current = new ArrayList<>();
        for (int index = 0; index < milestones.size(); index++) {
            if (milestones.get(index).path("current").asBoolean()) {
                current.add(index);
            }
        }
        // 아무것도 하지 않은 사용자는 맨 앞 단계에 있다. current 는 하나뿐이다.
        assertThat(current).containsExactly(0);
    }

    /**
     * AC-38 S2 · ADR-070: **자기평가만 올려도 단계가 넘어가지 않는다.** 그리고 Today와 대시보드가 같은 단계를 가리킨다.
     *
     * <p>전에는 대시보드가 계획 레벨만 비교하고 `lastPracticedAt`을 보지 않아서, 자기평가로 수준을 올린 사용자에게 Today는 1단계를 주는데 대시보드는
     * 다음 단계로 넘어간 것처럼 보일 수 있었다(docs/05 §13.1).
     */
    @Test
    void shouldNotAdvanceTheTimelineOnSelfAssessmentAlone() throws Exception {
        TestUser user = onboardedOwner();
        // 열네 category를 자기평가 상한(3)까지 올린다. 학습 기록은 하나도 만들지 않는다.
        List<Map<String, Object>> assessments = new ArrayList<>();
        for (SkillCategory category : SkillCategory.values()) {
            assessments.add(Map.of("category", category.name(), "level", 3));
        }
        api.put(user, "/api/v1/skills/me/self-assessment", Map.of("assessments", assessments))
                .andExpect(status().isOk());

        JsonNode milestones =
                api.body(api.get(user, DASHBOARD).andExpect(status().isOk()))
                        .path("milestoneTimeline")
                        .path("milestones");

        List<Integer> current = new ArrayList<>();
        for (int index = 0; index < milestones.size(); index++) {
            if (milestones.get(index).path("current").asBoolean()) {
                current.add(index);
            }
        }
        // 학습 기록이 없으므로 여전히 맨 앞 단계다 — ADR-049 "주장은 증거가 아니다".
        assertThat(current).containsExactly(0);

        // 같은 사용자의 Today도 1단계 skill을 낸다 (두 화면이 같은 단계를 가리킨다).
        JsonNode plan = api.generateToday(user, 60, "NORMAL");
        assertThat(plan.path("mainTask").isNull()).isFalse();
    }

    /** docs/05 §13.1: category별 평균은 활성 plan의 deferred=false skill만 세고 4축 평균 milli다. */
    @Test
    void shouldSummarizeSkillCategoriesFromTheActivePlan() throws Exception {
        TestUser user = onboardedOwner();
        // ADR-055: 카드는 그 skill을 배울 때 생긴다. 복습을 시험하려면 이미 배운 사람으로 시작한다
        assignSeedCardsAsIfStudied(user);

        JsonNode categories =
                api.body(api.get(user, DASHBOARD).andExpect(status().isOk()))
                        .path("skillCategories");

        assertThat(categories).isNotEmpty();
        for (JsonNode category : categories) {
            assertThat(category.path("category").asString()).isNotBlank();
            assertThat(category.path("skillCount").asInt()).isPositive();
            // 목표는 0보다 크다 — 그래서 "얼마나 남았는지"가 보인다.
            assertThat(category.path("avgTargetLevelMilli").asInt()).isPositive();
            // planning은 자기평가가 반영돼 0이 아닐 수 있다(docs/06 §7.5). 아직 목표에는 못 미친다.
            assertThat(category.path("avgPlanningLevelMilli").asInt())
                    .isNotNegative()
                    .isLessThan(category.path("avgTargetLevelMilli").asInt());
        }
        // 자기평가한 JAVA는 planning이 이미 0보다 크다 — 온보딩 입력이 반영됐다는 뜻이다.
        JsonNode java =
                StreamSupport.stream(categories.spliterator(), false)
                        .filter(category -> "JAVA".equals(category.path("category").asString()))
                        .findFirst()
                        .orElseThrow();
        assertThat(java.path("avgPlanningLevelMilli").asInt()).isPositive();
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

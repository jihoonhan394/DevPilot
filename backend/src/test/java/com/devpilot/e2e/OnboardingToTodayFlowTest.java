package com.devpilot.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/09 §11 E2E-01 온보딩 → Today → 세션 완료 (AC-02, AC-11, AC-17). 공통 시작 시각 2026-10-05T10:00:00Z(KST
 * 19:00, plan-day 2026-10-05, dayStartHour 4). 같은 context의 MockMvc로 요청 순서를 그대로 밟는다.
 */
@IntegrationTest
class OnboardingToTodayFlowTest extends ApiTestSupport {

    private static final List<String> CATEGORIES =
            List.of(
                    "JAVA",
                    "SPRING",
                    "DATABASE",
                    "WEB_HTTP",
                    "NETWORK",
                    "CS",
                    "ALGORITHM",
                    "TESTING",
                    "DEVOPS",
                    "SECURITY",
                    "INTEGRATION",
                    "PRACTICAL_ENGINEERING",
                    "SYSTEM_DESIGN",
                    "EXPLANATION");

    @Test
    void shouldGoFromOnboardingToCompletedSessionAcrossPlanDayBoundary() throws Exception {
        TestUser user = TestUser.owner();

        // 1. 첫 요청
        api.get(user, "/api/v1/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(false));
        UUID userId = userId(user);
        assertThat(count("select count(*) from devpilot.app_user where id = ?", userId))
                .isEqualTo(1);

        // 2. 온보딩 전 Today
        api.get(user, "/api/v1/today")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_REQUIRED"));

        // 3. 온보딩: 자기평가 14개, 사이드 프로젝트 건너뛰기
        Map<String, Object> onboarding = TestApi.onboardingRequest();
        List<Object> assessments = new ArrayList<>();
        for (String category : CATEGORIES) {
            assessments.add(Map.of("category", category, "level", category.equals("JAVA") ? 3 : 1));
        }
        onboarding.put("selfAssessments", assessments);
        api.post(user, "/api/v1/onboarding", onboarding)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activePlan.planVersion").value(1))
                .andExpect(jsonPath("$.sideProject").doesNotExist())
                .andExpect(jsonPath("$.assignedSeedCardCount").value(10));

        // 4. 다시 온보딩
        api.post(user, "/api/v1/onboarding", TestApi.onboardingRequest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ONBOARDING_ALREADY_COMPLETED"));

        // 5. 활성 plan
        JsonNode plan = activePlan(user);
        assertThat(plan.path("milestones").size()).isGreaterThanOrEqualTo(1);
        plan.path("skillTargets")
                .forEach(
                        target ->
                                assertThat(target.path("adjustment").asString())
                                        .isEqualTo("ROLE_DEFAULT"));

        // 6. seed 카드 10장: 앞 5장 start(D), 다음 5장 start(D + 1)
        List<String> due =
                jdbc.queryForList(
                        "select to_char(due_at at time zone 'UTC',"
                            + " 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"') from devpilot.review_item where"
                            + " user_id = ? order by due_at",
                        String.class,
                        userId);
        assertThat(due).hasSize(10);
        assertThat(due.subList(0, 5)).containsOnly("2026-10-04T19:00:00Z");
        assertThat(due.subList(5, 10)).containsOnly("2026-10-05T19:00:00Z");

        // 7. 생성 전 Today
        api.get(user, "/api/v1/today")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODAY_NOT_GENERATED"));

        // 8. 생성: REVIEW = min(ceilDiv(5 × 15000, 10000) = 8, 11, 40) = 8, main ≤ floorDiv(37 ×
        // 11000, 10000)
        JsonNode today = api.generateToday(user, 45, "NORMAL");
        JsonNode main = today.path("mainTask");
        assertThat(main.path("reasons").size()).isBetween(1, 3);
        assertThat(main.path("estimatedMinutes").asInt()).isLessThanOrEqualTo(40);
        assertThat(today.path("reviewTask").path("estimatedMinutes").asInt()).isEqualTo(8);
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_task where user_id = ? and"
                                        + " is_main",
                                userId))
                .isEqualTo(1);
        String mainId = main.path("id").asString();

        // 9. main 시작
        api.patch(
                        user,
                        "/api/v1/today/tasks/{taskId}",
                        Map.of("status", "IN_PROGRESS", "version", main.path("version").asLong()),
                        mainId)
                .andExpect(status().isOk());

        // 10. 세션 시작
        JsonNode session = api.startSession(user, mainId).path("session");
        assertThat(session.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(eventCount(userId, "SESSION_STARTED")).isEqualTo(1);

        // 11. 40분 뒤 35분으로 완료
        clock.advance(Duration.ofMinutes(40));
        api.post(
                        user,
                        "/api/v1/learning-sessions/{id}/complete",
                        Map.of("actualMinutes", 35),
                        session.path("id").asString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(
                        count(
                                "select count(*) from devpilot.learning_event where user_id = ? and"
                                        + " event_type = 'SESSION_COMPLETED' and plan_date = date"
                                        + " '2026-10-05'",
                                userId))
                .isEqualTo(1);

        // 12. main 완료 (READ_CODE면 러버덕을 먼저 마친다 — RC-1)
        JsonNode mainTask = api.body(api.get(user, "/api/v1/today")).path("mainTask");
        satisfyCodeReadingCondition(user, mainTask);
        long version = mainTask.path("version").asLong();
        api.patch(
                        user,
                        "/api/v1/today/tasks/{taskId}",
                        Map.of("status", "COMPLETED", "version", version),
                        mainId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedAt").value("2026-10-05T10:40:00Z"));

        // 13. 완료 후 재생성
        api.post(user, "/api/v1/today/generate", TestApi.todayRequest(45, "NORMAL", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TODAY_ALREADY_COMPLETED"));

        // 14. KST 03:59:59는 아직 같은 plan-day
        clock.setInstant(Instant.parse("2026-10-05T18:59:59Z"));
        api.get(user, "/api/v1/today")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planDate").value("2026-10-05"));

        // 15. KST 04:00부터 새 plan-day
        clock.setInstant(Instant.parse("2026-10-05T19:00:00Z"));
        api.get(user, "/api/v1/today")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TODAY_NOT_GENERATED"));
    }

    private int eventCount(UUID userId, String type) {
        return count(
                "select count(*) from devpilot.learning_event where user_id = ? and event_type = ?",
                userId,
                type);
    }
}

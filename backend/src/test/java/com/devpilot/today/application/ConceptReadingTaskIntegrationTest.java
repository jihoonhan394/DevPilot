package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestApi;
import com.devpilot.testsupport.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * docs/06 §5.3 "개념 읽기 선택", docs/19 §3.13, docs/05 §8.1·§19.7: {@code READING} 과제가 읽을 공식 문서를 가리킨다.
 * 후보가 없는 skill의 {@code READING}은 전과 똑같이 자료 없이 제안된다(회귀 없음).
 */
@IntegrationTest
class ConceptReadingTaskIntegrationTest extends ApiTestSupport {

    /** 개념 읽기는 있고 코드 읽기·challenge는 없는 skill (test-content). */
    private static final String SKILL_WITH_MATERIAL = "WEB_HTTP.HTTP_BASICS";

    private static final String CONCEPT_KEY = "DOC.TESTHTTP.METHOD.001";

    @Test
    void shouldProposeReadingWithConceptReadingKey() throws Exception {
        TestUser user = focusedOn(SKILL_WITH_MATERIAL);

        JsonNode main = api.generateToday(user, 60, "NORMAL").path("mainTask");

        assertThat(main.path("skillCode").asString()).isEqualTo(SKILL_WITH_MATERIAL);
        assertThat(main.path("taskType").asString()).isEqualTo("READING");
        assertThat(main.path("readingKey").asString()).isEqualTo(CONCEPT_KEY);
        assertThat(main.path("estimatedMinutes").asInt()).isEqualTo(20);
        assertThat(main.path("title").asString()).contains("개념 읽기 —");
        assertThat(main.path("description").asString()).contains("핵심 3가지를 스스로 적어 보세요");
        assertThat(main.path("challengeId").isNull()).isTrue();
        assertThat(main.path("sideProjectId").isNull()).isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select reading_key from devpilot.learning_task where id = ?::uuid",
                                String.class,
                                main.path("id").asString()))
                .isEqualTo(CONCEPT_KEY);

        // 화면은 이 key로 자료를 가져온다 (docs/02 SCR-TODAY, docs/05 §19.7)
        api.get(user, "/api/v1/readings/{readingKey}", CONCEPT_KEY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("CONCEPT"))
                .andExpect(jsonPath("$.concept.checkPoints.length()").value(3));
    }

    @Test
    void shouldFallBackToReadingWithoutMaterialWhenNoCandidateIsLeft() throws Exception {
        // docs/06 §5.3 C-3·C-5: 최근 14 plan-day 안에 제안된 자료는 후보에서 빠지고,
        // 후보가 비면 자료 없이 전과 똑같은 READING이 제안된다(회귀 없음)
        TestUser user = focusedOn(SKILL_WITH_MATERIAL);
        JsonNode first = api.generateToday(user, 60, "NORMAL").path("mainTask");
        String taskId = first.path("id").asString();
        api.patch(
                        user,
                        "/api/v1/today/tasks/{taskId}",
                        Map.of("status", "SKIPPED", "version", 0),
                        taskId)
                .andExpect(status().isOk());

        JsonNode second =
                api.body(
                                api.post(
                                                user,
                                                "/api/v1/today/generate",
                                                TestApi.todayRequest(60, "NORMAL", true))
                                        .andExpect(status().isOk()))
                        .path("mainTask");

        assertThat(second.path("skillCode").asString()).isEqualTo(SKILL_WITH_MATERIAL);
        assertThat(second.path("taskType").asString()).isEqualTo("READING");
        assertThat(second.path("readingKey").isNull()).isTrue();
        assertThat(second.path("estimatedMinutes").asInt()).isEqualTo(25);
        assertThat(second.path("title").asString()).endsWith("핵심 개념 정리");
        assertThat(
                        jdbc.queryForObject(
                                "select reading_key from devpilot.learning_task where id = ?::uuid",
                                String.class,
                                second.path("id").asString()))
                .isNull();
    }

    /** 그 skill이 오늘의 main이 되도록 학습 목표의 집중 skill로 둔 사용자. */
    private TestUser focusedOn(String skillCode) throws Exception {
        TestUser user = TestUser.owner();
        Map<String, Object> request = TestApi.onboardingRequest();
        @SuppressWarnings("unchecked")
        Map<String, Object> learningGoal = (Map<String, Object>) request.get("learningGoal");
        learningGoal.put("focusSkillCodes", new ArrayList<>(List.of(skillCode)));
        api.onboard(user, request);
        skipSeedPracticeChallenges(user);
        return user;
    }
}

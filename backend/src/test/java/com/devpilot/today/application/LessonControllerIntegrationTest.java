package com.devpilot.today.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.devpilot.testsupport.ApiTestSupport;
import com.devpilot.testsupport.IntegrationTest;
import com.devpilot.testsupport.TestUser;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 개념 노트 API (docs/05 §21, AC-37). 가르치는 단계가 실제로 도는지 본다.
 *
 * <p>노트는 테스트 fixture 콘텐츠({@code test-content/lessons/test.yaml})의 것을 쓴다 — registry를 테스트에서 갈아끼우면 같은
 * context를 쓰는 격리 테스트의 공용 콘텐츠가 사라진다.
 */
@IntegrationTest
class LessonControllerIntegrationTest extends ApiTestSupport {

    private static final String LESSON_KEY = "LESSON.TESTSPRING.MVC.001";
    private static final String UNIT_KEY = LESSON_KEY + ".U1";
    private static final String LESSON = "/api/v1/lessons/{lessonKey}";
    private static final String UNIT = "/api/v1/lessons/{lessonKey}/units/{unitKey}";

    /** AC-37 S1: 노트를 조회해도 답이 오지 않는다. */
    @Test
    void shouldNotSendAnswersWithTheLesson() throws Exception {
        TestUser user = onboardedUser();

        JsonNode lesson = api.body(api.get(user, LESSON, LESSON_KEY).andExpect(status().isOk()));

        assertThat(lesson.path("units")).hasSize(3);
        JsonNode unit = lesson.path("units").get(0);
        assertThat(unit.path("explain").asString()).isNotBlank();
        assertThat(unit.path("example").path("code").asString()).contains("@GetMapping");
        assertThat(unit.path("predict").path("choices")).hasSize(2);
        assertThat(unit.path("complete").path("blanks").asInt()).isEqualTo(1);
        assertThat(unit.path("problem").path("hints")).hasSize(2);
        assertThat(unit.path("progress").isNull()).isTrue();

        String json = lesson.toString();
        assertThat(json).doesNotContain("modelAnswer").doesNotContain("selfChecks");
        assertThat(json).doesNotContain("\"answer\"").doesNotContain("\"answers\"");
    }

    /** AC-37 S2: 예측·빈칸은 서버가 즉시 채점한다. */
    @Test
    void shouldGradePredictAndCompleteWithoutAi() throws Exception {
        TestUser user = onboardedUser();
        int callsBefore = aiCallCount();

        JsonNode trimmed =
                api.body(
                        api.post(
                                        user,
                                        UNIT + "/predict",
                                        Map.of("answer", "  400  "),
                                        LESSON_KEY,
                                        UNIT_KEY)
                                .andExpect(status().isOk()));
        assertThat(trimmed.path("correct").asBoolean()).isTrue();
        assertThat(trimmed.path("expected").asString()).isEqualTo("400");

        JsonNode wrongCase =
                api.body(
                        api.post(
                                        user,
                                        UNIT + "/predict",
                                        Map.of("answer", "사백"),
                                        LESSON_KEY,
                                        UNIT_KEY)
                                .andExpect(status().isOk()));
        assertThat(wrongCase.path("correct").asBoolean()).isFalse();

        JsonNode filled =
                api.body(
                        api.post(
                                        user,
                                        UNIT + "/complete",
                                        Map.of("answers", List.of("GetMapping")),
                                        LESSON_KEY,
                                        UNIT_KEY)
                                .andExpect(status().isOk()));
        assertThat(filled.path("correct").asBoolean()).isTrue();
        assertThat(filled.path("results").get(0).asBoolean()).isTrue();

        assertThat(aiCallCount()).isEqualTo(callsBefore);
    }

    /** AC-37 S2: 빈칸 수와 답 개수가 다르면 400이다. */
    @Test
    void shouldRejectWhenTheAnswerCountDoesNotMatchTheBlanks() throws Exception {
        TestUser user = onboardedUser();

        api.post(
                        user,
                        UNIT + "/complete",
                        Map.of("answers", List.of("GetMapping", "extra")),
                        LESSON_KEY,
                        UNIT_KEY)
                .andExpect(status().isBadRequest());
    }

    /** AC-37 S3: 모범 답안은 조회이고 상태를 바꾸지 않는다. */
    @Test
    void shouldRevealTheModelAnswerWithoutRecordingAnything() throws Exception {
        TestUser user = onboardedUser();
        long before = unitEvents(user);

        JsonNode answer =
                api.body(
                        api.get(user, UNIT + "/answer", LESSON_KEY, UNIT_KEY)
                                .andExpect(status().isOk()));

        assertThat(answer.path("modelAnswer").asString()).contains("@GetMapping");
        assertThat(answer.path("selfChecks")).hasSize(2);
        assertThat(unitEvents(user)).isEqualTo(before);
    }

    /** AC-37 S4: 단위를 마치면 기록이 한 번 남고 진행이 보인다. */
    @Test
    void shouldRecordOnceWhenTheUnitIsFinished() throws Exception {
        TestUser user = onboardedUser();
        Map<String, Object> request = Map.of("helpLevel", "HINT", "selfChecksMet", 1);

        String key = UUID.randomUUID().toString();
        api.postWithKey(user, key, UNIT + "/finish", request, LESSON_KEY, UNIT_KEY)
                .andExpect(status().isOk());
        api.postWithKey(user, key, UNIT + "/finish", request, LESSON_KEY, UNIT_KEY)
                .andExpect(status().isOk());

        assertThat(unitEvents(user)).isEqualTo(1);
        Map<String, Object> event =
                jdbc.queryForMap(
                        "select payload::text as payload, skill_id from devpilot.learning_event"
                                + " where user_id = ? and event_type = 'UNIT_SOLVED'",
                        userId(user));
        String payload = String.valueOf(event.get("payload")).replace(" ", "");
        assertThat(payload)
                .contains("\"unitKey\":\"" + UNIT_KEY + "\"")
                .contains("\"helpLevel\":\"HINT\"")
                .contains("\"selfChecksMet\":1");
        assertThat(event.get("skill_id")).isNotNull();

        JsonNode lesson = api.body(api.get(user, LESSON, LESSON_KEY).andExpect(status().isOk()));
        JsonNode progress = lesson.path("units").get(0).path("progress");
        assertThat(progress.path("solved").asBoolean()).isTrue();
        assertThat(progress.path("helpLevel").asString()).isEqualTo("HINT");
        assertThat(progress.path("selfChecksMet").asInt()).isEqualTo(1);
    }

    /** AC-37 S4: 확인 목록보다 많이 체크하면 400이다. */
    @Test
    void shouldRejectWhenMoreSelfChecksThanTheUnitHas() throws Exception {
        TestUser user = onboardedUser();

        api.postWithKey(
                        user,
                        UUID.randomUUID().toString(),
                        UNIT + "/finish",
                        Map.of("helpLevel", "NONE", "selfChecksMet", 5),
                        LESSON_KEY,
                        UNIT_KEY)
                .andExpect(status().isBadRequest());
    }

    /** AC-37 S7: 없는 key는 404, 형식이 어긋나면 400, 토큰이 없으면 401이다. */
    @Test
    void shouldAnswerWithTheRightStatusForUnknownKeys() throws Exception {
        TestUser user = onboardedUser();

        api.get(user, LESSON, "LESSON.NOPE.001").andExpect(status().isNotFound());
        api.get(user, LESSON, "not-a-key").andExpect(status().isBadRequest());
        api.mockMvc()
                .perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                "/api/v1/lessons/{lessonKey}", LESSON_KEY))
                .andExpect(status().isUnauthorized());
    }

    private TestUser onboardedUser() throws Exception {
        TestUser user = TestUser.owner();
        api.onboard(user);
        return user;
    }

    private long unitEvents(TestUser user) {
        Long count =
                jdbc.queryForObject(
                        "select count(*) from devpilot.learning_event"
                                + " where user_id = ? and event_type = 'UNIT_SOLVED'",
                        Long.class,
                        userId(user));
        return count == null ? 0 : count;
    }

    private int aiCallCount() {
        Integer count =
                jdbc.queryForObject("select count(*) from devpilot.ai_call_log", Integer.class);
        return count == null ? 0 : count;
    }
}
